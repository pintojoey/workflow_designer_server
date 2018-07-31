package cz.zcu.kiv.server.scheduler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static cz.zcu.kiv.server.Workflow.*;

public class Job {
    private static Log logger = LogFactory.getLog(Job.class);

    private long id;
    private JSONObject workflow;
    private Date startTime;
    private Date endTime;
    private Status status;
    private String workflowOutputFile;
    private String owner;

    public Job(JSONObject workflowObject) {
        this.workflow=workflowObject;
        this.startTime=new Date();
        this.status=Status.WAITING;

    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public JSONObject getWorkflow() {
        return workflow;
    }

    public void setWorkflow(JSONObject workflow) {
        this.workflow = workflow;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getWorkflowOutputFile() {
        return workflowOutputFile;
    }

    public void setWorkflowOutputFile(String workflowOutputFile) {
        this.workflowOutputFile = workflowOutputFile;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void execute(){
        setStatus(Status.RUNNING);
        Set<String> modules=new HashSet<>();
        try {

            JSONArray blocksArray = workflow.getJSONArray("blocks");
            for(int i=0;i<blocksArray.length();i++){
                modules.add(blocksArray.getJSONObject(i).getString("module"));
            }

        }
        catch (JSONException e){
            logger.error("Error parsing workflow JSON",e);
            setStatus(Status.FAILED);
            return;
        }

        ClassLoader child;
        Map<Class,String> moduleSource = new HashMap<>();

        try {
            child = initializeClassLoader(modules,moduleSource);
        } catch (IOException e) {
            logger.error("Cannot read jar from server",e);
            setStatus(Status.FAILED);
            return;
        }

        JSONArray result;

        try{
            File workflowOutputFile = File.createTempFile("job_"+getId(),".json",new File(WORKING_DIRECTORY));
            setWorkflowOutputFile(workflowOutputFile.getAbsolutePath());
            result=executeJar(child,workflow,moduleSource,workflowOutputFile.getAbsolutePath());
            for(int i=0;i<result.length();i++){
                if(result.getJSONObject(i).getBoolean("error")){
                    logger.error("Executing jar failed");
                    setStatus(Status.FAILED);
                    return;
                }

            }
        }
        catch(Exception e1){
            logger.error("Executing jar failed",e1);
            setStatus(Status.FAILED);
            return;
        }
        if(result!=null){
            setStatus(Status.COMPLETED);
        }
        else{
            logger.error("No result was generated");
            setStatus(Status.FAILED);
        }
        setEndTime(new Date());
    }

    public JSONObject toJSON(boolean withWorkflow) {
        JSONObject jsonObject=new JSONObject();
        jsonObject.put("id",getId());
        jsonObject.put("startTime",getStartTime().toString());
        jsonObject.put("endTime",getEndTime()!=null?getEndTime().toString():"");
        jsonObject.put("status",getStatus().name());
        if(withWorkflow){
            jsonObject.put("workflow",getWorkflow());
            if(workflowOutputFile!=null) {
                try {
                    String workflowExecutionStatus = FileUtils.readFileToString(new File(workflowOutputFile),Charset.defaultCharset());
                    if(!workflowExecutionStatus.isEmpty())
                        jsonObject.put("executionStatus", new JSONArray(workflowExecutionStatus));
                } catch (IOException e) {
                   logger.error(e);
                }
            }
        }

        return jsonObject;
    }
}
