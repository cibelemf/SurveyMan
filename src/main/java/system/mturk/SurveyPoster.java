package system.mturk;

import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.*;
import com.amazonaws.mturk.requester.HIT;
import java.io.*;
import csv.CSVParser;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import survey.Survey;

public class SurveyPoster {

    private static final String fileSep = System.getProperty("file.separator");
    private static final String config = ".config";
    private static final RequesterService service = new RequesterService(new PropertiesClientConfig(config));
    public static final String outDir = "data/output";
    public static HITProperties parameters;
    
    static {
        try {
            parameters = new HITProperties("params.properties");
        } catch (IOException ex) {
            ex.printStackTrace();
            //Logger.getLogger(SurveyPoster.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }
    }
    
    public static boolean hasEnoughFund() {
        double balance = service.getAccountBalance();
        System.out.println("Got account balance: " + RequesterService.formatCurrency(balance));
        return balance > 0;
    }
    
    public static String postSurvey(Survey survey) throws IOException {
        HIT hit = service.createHIT(parameters.getTitle()
                , parameters.getDescription()
                , parameters.getRewardAmount()
                , XMLGenerator.getXMLString(survey)
                , parameters.getMaxAssignments()
                );
        String hitid = hit.getHITId();
        String hittypeid = hit.getHITTypeId();
        System.out.println("Created HIT: " + hitid);
        System.out.println("You may see your HIT with HITTypeId '" + hittypeid + "' here: ");
        System.out.println(service.getWebsiteURL() + "/mturk/preview?groupId=" + hittypeid);
        recordHit(hitid, hittypeid);
        return hitid;
    }

    private static void recordHit(String hitid, String hittypeid) throws IOException {
        PrintWriter out = new PrintWriter(new FileWriter(new File(outDir + "/survey.success"), true));
        out.println(hitid+","+hittypeid);
    }
    
    public static void main(String[] args) throws Exception {
        Survey survey3 = CSVParser.parse(String.format("data%1$slinguistics%1$stest3.csv", fileSep), ":");
        Survey survey2 = CSVParser.parse(String.format("data%1$slinguistics%1$stest2.csv", fileSep), "\\t");
        //Survey survey1 = CSVParser.parse(String.format("data%1$slinguistics%1$stest1.csv", fileSep), ",");
        Survey[] surveys = {survey2,survey3};
        for (Survey survey : Arrays.asList(surveys)) {
            HITQuestion hitq = new HITQuestion();
            hitq.setQuestion(XMLGenerator.getXMLString(survey));
            //service.previewHIT(null,parameters,hitq);
            String hitid =  postSurvey(survey) ;
//            service.disableHIT(hitid);
//            service.disposeHIT(hitid);
//            service.deleteHITs(hitids, false, true, new BatchItemCallback() {
//                @Override
//                public void processItemResult(Object itemId, boolean succeeded, Object result, Exception itemException) {
//
//                }
//            });
        }
        if (service.getTotalNumHITsInAccount() != 0)
            Logger.getAnonymousLogger().log(Level.WARNING, "Total registered HITs is " + service.getTotalNumHITsInAccount());
    }
}
