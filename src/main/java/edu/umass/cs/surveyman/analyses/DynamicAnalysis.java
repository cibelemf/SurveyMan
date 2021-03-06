package edu.umass.cs.surveyman.analyses;

import edu.umass.cs.surveyman.SurveyMan;
import edu.umass.cs.surveyman.output.*;
import edu.umass.cs.surveyman.qc.*;
import edu.umass.cs.surveyman.survey.*;
import edu.umass.cs.surveyman.survey.exceptions.SurveyException;
import org.apache.commons.lang3.StringUtils;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.util.*;

public class DynamicAnalysis {

    public static class QuestionResponse implements IQuestionResponse {

        private Question q;
        private List<OptTuple> opts = new ArrayList<OptTuple>();
        private int indexSeen;

        public QuestionResponse(
                Survey s,
                String quid,
                int qpos)
                throws SurveyException
        {
            this.q = s.getQuestionById(quid);
            this.indexSeen = qpos;
        }

        @Override
        public Question getQuestion()
        {
            return q;
        }

        @Override
        public List<OptTuple> getOpts()
        {
            return opts;
        }

        @Override
        public int getIndexSeen()
        {
            return indexSeen;
        }

        @Override
        public Component getAnswer() throws SurveyException
        {
            if (this.getQuestion().exclusive)
                return this.getOpts().get(0).c;
            else throw new RuntimeException("Cannot call getAnswer() on non-exclusive questions. Try getAnswers() instead.");
        }

        @Override
        public List<Component> getAnswers() throws SurveyException
        {
            if (this.getQuestion().exclusive)
                throw new RuntimeException("Cannot call getAnswers() on exclusive questions. Try getAnswer() instead.");
            List<Component> answers = new ArrayList<Component>();
            for (OptTuple optTuple : this.getOpts())
                answers.add(optTuple.c);
            return answers;
        }

        @Override
        public int compareTo(Object o)
        {
            if (o instanceof IQuestionResponse) {
                IQuestionResponse that = (IQuestionResponse) o;
                return this.getQuestion().compareTo(that.getQuestion());
            } else throw new RuntimeException(String.format("Cannot compare classes %s and %s",
                    this.getClass().getName(), o.getClass().getName()));
        }
    }

    public static class DynamicSurveyResponse extends SurveyResponse implements ISurveyResponseReader {

        public DynamicSurveyResponse(String srid) {
            super();
            this.setSrid(srid);
        }

        @Override
        public Map<String, IQuestionResponse> resultsAsMap()
        {
            Map<String, IQuestionResponse> retval = new HashMap<String, IQuestionResponse>();
            for (IQuestionResponse qr : this.getNonCustomResponses()) {
                retval.put(qr.getQuestion().quid, qr);
            }
            return retval;
        }

        @Override
        public List<? extends SurveyResponse> readSurveyResponses(
                Survey s,
                Reader r)
                throws SurveyException
        {
            List<SurveyResponse> responses = new LinkedList<SurveyResponse>();
            final CellProcessor[] cellProcessors = s.makeProcessorsForResponse();
            try{
                ICsvMapReader reader = new CsvMapReader(r, CsvPreference.STANDARD_PREFERENCE);
                final String[] header = reader.getHeader(true);
                Map<String, Object> headerMap;
                SurveyResponse sr = null;
                while ((headerMap = reader.read(header, cellProcessors)) != null) {
                // loop through one survey response (i.e. per responseid) at a time
                    if ( sr == null || !sr.getSrid().equals(headerMap.get("responseid"))){
                        if (sr!=null)
                        // add this to the list of responses and create a new one
                            responses.add(sr);
                        sr = new DynamicSurveyResponse((String) headerMap.get("workerid"));
                        sr.setSrid((String) headerMap.get("responseid"));
                    }
                    // fill out the individual question responses
                    IQuestionResponse questionResponse =
                            new QuestionResponse(
                                    s,
                                    (String) headerMap.get("questionid"),
                                    (Integer) headerMap.get("questionpos"));
                    for (IQuestionResponse qr : sr.getNonCustomResponses())
                        if (qr.getQuestion().quid.equals((String) headerMap.get("questionid"))) {
                        // if we already have a QuestionResponse object matching this id, set it
                            questionResponse = qr;
                            break;
                        }
                    Component c;
                    if (!Question.customQuestion(questionResponse.getQuestion().quid))
                        c = questionResponse.getQuestion().getOptById((String) headerMap.get("optionid"));
                    else c = new StringComponent((String) headerMap.get("optionid"), -1, -1);
                    Integer i = (Integer) headerMap.get("optionpos");
                    questionResponse.getOpts().add(new OptTuple(c,i));
                    sr.getNonCustomResponses().add(questionResponse);
                }
                reader.close();
                return responses;
            } catch (IOException io) {
                io.printStackTrace();
            }
            return null;
        }

        @Override
        public boolean surveyResponseContainsAnswer(
                List<Component> variants)
        {
            throw new RuntimeException("Should not be calling surveyResponseContainsAnswer from inside Dynamic Analysis.");
        }

    }

    public static class Report {

        public final String surveyName;
        public final String sid;
        public final double alpha;
        public final boolean smoothing;
        public final OrderBiasStruct orderBiases;
        public final WordingBiasStruct wordingBiases;
        public final BreakoffByQuestion breakoffByQuestion;
        public final BreakoffByPosition breakoffByPosition;
        public final ClassifiedRespondentsStruct classifiedResponses;

        public Report(
                String surveyName,
                String sid,
                double alpha,
                boolean smoothing,
                OrderBiasStruct orderBiases,
                WordingBiasStruct wordingBiases,
                BreakoffByPosition breakoffByPosition,
                BreakoffByQuestion breakoffByQuestion,
                ClassifiedRespondentsStruct classifiedResponses)
        {
            this.surveyName = surveyName;
            this.sid = sid;
            this.alpha = alpha;
            this.smoothing = smoothing;
            this.orderBiases = orderBiases;
            this.wordingBiases = wordingBiases;
            this.breakoffByPosition = breakoffByPosition;
            this.breakoffByQuestion = breakoffByQuestion;
            this.classifiedResponses = classifiedResponses;
        }


        public void print(
                OutputStream stream)
        {
            OutputStreamWriter osw = new OutputStreamWriter(stream);
            try {
                String[] toprint = {
                        this.orderBiases.toString(),
                        this.wordingBiases.toString(),
                        this.breakoffByPosition.toString(),
                        this.breakoffByQuestion.toString(),
                        this.classifiedResponses.toString()
                };
                osw.write(StringUtils.join(toprint, "\n"));
                osw.flush();
            } catch (IOException io) {
                io.printStackTrace();
                SurveyMan.LOGGER.warn(io);
            }
        }
    }

    public static Report dynamicAnalysis(
            Survey survey,
            List<DynamicSurveyResponse> responses,
            Classifier classifier,
            boolean smoothing,
            double alpha)
            throws SurveyException {
        return new Report(
                survey.sourceName,
                survey.sid,
                alpha,
                smoothing,
                QCMetrics.calculateOrderBiases(survey, responses, alpha),
                QCMetrics.calculateWordingBiases(survey, responses, alpha),
                QCMetrics.calculateBreakoffByPosition(survey, responses),
                QCMetrics.calculateBreakoffByQuestion(survey, responses),
                QCMetrics.classifyResponses(survey, responses, classifier, smoothing, alpha)
            );
   }


    public static List<DynamicSurveyResponse> readSurveyResponses(
            Survey s,
            String filename)
            throws SurveyException
    {
        List<DynamicSurveyResponse> responses = null;
        if (new File(filename).isFile()) {
            try {
                responses = readSurveyResponses(s, new FileReader(filename));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else if (new File(filename).isDirectory()) {
            responses = new ArrayList<DynamicSurveyResponse>();
            for (File f : new File(filename).listFiles()) {
                try {
                    responses.addAll(readSurveyResponses(s, new FileReader(f)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } else throw new RuntimeException("Unknown file or directory: "+filename);
        return responses;
   }

    public static List<DynamicSurveyResponse> readSurveyResponses(
            Survey s,
            Reader r)
            throws SurveyException
    {
        List<DynamicSurveyResponse> responses = new LinkedList<DynamicSurveyResponse>();
        final CellProcessor[] cellProcessors = s.makeProcessorsForResponse();
        try{
            ICsvMapReader reader = new CsvMapReader(r, CsvPreference.STANDARD_PREFERENCE);
            final String[] header = reader.getHeader(true);
            Map<String, Object> headerMap;
            DynamicSurveyResponse sr = null;
            while ((headerMap = reader.read(header, cellProcessors)) != null) {
                // loop through one survey response (i.e. per responseid) at a time
                if ( sr == null || !sr.getSrid().equals(headerMap.get("responseid"))){
                    if (sr!=null)
                    // add this to the list of responses and create a new one
                        responses.add(sr);
                    sr = new DynamicSurveyResponse((String) headerMap.get("workerid"));
                    sr.setSrid((String) headerMap.get("responseid"));
                }
                // fill out the individual question responses
                IQuestionResponse questionResponse = new QuestionResponse(
                        s,
                        (String) headerMap.get("questionid"),
                        (Integer) headerMap.get("questionpos"));
                for (IQuestionResponse qr : sr.getNonCustomResponses())
                    if (qr.getQuestion().quid.equals((String) headerMap.get("questionid"))) {
                    // if we already have a QuestionResponse object matching this id, set it
                        questionResponse = qr;
                        break;
                    }
                Component c;
                if (!Question.customQuestion(questionResponse.getQuestion().quid))
                    c = questionResponse.getQuestion().getOptById((String) headerMap.get("optionid"));
                else c = new StringComponent((String) headerMap.get("optionid"), -1, -1);
                Integer i = (Integer) headerMap.get("optionpos");
                questionResponse.getOpts().add(new OptTuple(c,i));
                sr.addResponse(questionResponse);
            }
            reader.close();
            return responses;
        } catch (IOException io) {
            io.printStackTrace();
        }
        return null;
    }

}