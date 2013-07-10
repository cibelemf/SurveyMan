package system.mturk;

import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.*;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.requester.HITStatus;
import com.amazonaws.mturk.service.exception.InternalServiceException;
//import com.amazonaws.mturk.requester.QualificationType;
import java.io.*;
import csv.CSVParser;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import survey.Survey;
import survey.SurveyException;

public class SurveyPoster {

    private static final String fileSep = System.getProperty("file.separator");
    private static PropertiesClientConfig config = new PropertiesClientConfig(MturkLibrary.CONFIG);
    protected static RequesterService service;
    public static HITProperties parameters;
    //public static QualificationType alreadySeen = service.createQualificationType("survey", "survey", QC.QUAL);
    static {
        try {
            parameters = new HITProperties(MturkLibrary.PARAMS);
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            System.exit(-1);
        }
        config.setServiceURL(MturkLibrary.MTURK_URL);
        service = new RequesterService(config);
    }
    
    public static boolean hasEnoughFund() {
        double balance = service.getAccountBalance();
        System.out.println("Got account balance: " + RequesterService.formatCurrency(balance));
        return balance > 0;
    }
    
    public static void expireOldHITs() {
        boolean expired = false;
        while (! expired) {
            try {
                for (HIT hit : service.searchAllHITs()){
                    HITStatus status = hit.getHITStatus();
                    if (! (status.equals(HITStatus.Reviewable) || status.equals(HITStatus.Reviewing))) {
                        service.disableHIT(hit.getHITId());
                        System.out.println("Expired HIT:"+hit.getHITId());
                    }
                }
                expired = true;
            } catch (Exception e) {
                System.err.println("WARNING: "+e.getMessage());
            }
        }
        System.out.println("Total HITs available before execution: " + service.getTotalNumHITsInAccount());
    }

    public static HIT postSurvey(Survey survey) throws SurveyException {
        boolean notRecorded = true;
        HIT hit = null;
        while (notRecorded) {
            try {                
                hit = service.createHIT(parameters.getTitle()
                        , parameters.getDescription()
                        , parameters.getRewardAmount()
                        , XMLGenerator.getXMLString(survey)
                        , parameters.getMaxAssignments()
                        , true
                        );
                String hitid = hit.getHITId();
                String hittypeid = hit.getHITTypeId();
                System.out.println(String.format("Created HIT: %1$s \r\n You may see your HIT with HITTypeId '%2$s' here: %3$s/mturk/preview?groupId=%2$s"
                        , hitid
                        , hittypeid
                        , service.getWebsiteURL()));
                recordHit(hitid, hittypeid);
                notRecorded = false;
            } catch (InternalServiceException e) {
                System.err.println("WARNING: ("+e.getClass().getName()+")" + e.getMessage());
            }
        }
        return hit;
    }     

    private static void recordHit(String hitid, String hittypeid) {
        try { 
            PrintWriter out = new PrintWriter(new FileWriter(ResponseManager.SUCCESS, true));
            out.println(hitid+","+hittypeid);
            out.close();
        } catch (IOException io) {
            System.out.println(String.format("WARNING: %s.", io.getMessage()));
        }
    }
    
    public static void main(String[] args) throws Exception {
        expireOldHITs();
        Survey survey3 = CSVParser.parse(String.format("data%1$slinguistics%1$stest3.csv", fileSep), ":");
        Survey survey2 = CSVParser.parse(String.format("data%1$slinguistics%1$stest2.csv", fileSep), "\\t");
        //Survey survey1 = CSVParser.parse(String.format("data%1$slinguistics%1$stest1.csv", fileSep), ",");
        Survey[] surveys = {survey2,survey3};
        for (Survey survey : Arrays.asList(surveys)) {
            HITQuestion hitq = new HITQuestion();
            hitq.setQuestion(XMLGenerator.getXMLString(survey));
            //service.previewHIT(null,parameters,hitq);
            postSurvey(survey);
        }
        if (service.getTotalNumHITsInAccount() != 0)
            Logger.getAnonymousLogger().log(Level.WARNING, "Total registered HITs is {0}", service.getTotalNumHITsInAccount());
    }
}