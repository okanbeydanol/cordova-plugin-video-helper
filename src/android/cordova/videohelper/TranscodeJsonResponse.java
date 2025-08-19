package com.okanbeydanol.videoHelper;


import org.json.JSONException;
import org.json.JSONObject;

class TranscodeCompletedJsonResponse implements JsonResponseCreator {
    private final String outputPath;

    public TranscodeCompletedJsonResponse(String outputPath) {
        this.outputPath = outputPath;
    }

    @Override
    public JSONObject createResponse() throws JSONException {
        JSONObject completedJson = new JSONObject();
        completedJson.put("progress", 100);
        completedJson.put("completed", true);
        completedJson.put("error", false);
        completedJson.put("message", "Completed!");
        completedJson.put("data", outputPath);
        return completedJson;
    }
}

class TranscodeCanceledJsonResponse implements JsonResponseCreator {
    @Override
    public JSONObject createResponse() throws JSONException {
        JSONObject canceledJson = new JSONObject();
        canceledJson.put("progress", 0);
        canceledJson.put("completed", false);
        canceledJson.put("error", true);
        canceledJson.put("message", "Transcode canceled!");
        return canceledJson;
    }
}

class TranscodeFailedJsonResponse implements JsonResponseCreator {
    private final Exception exception;

    public TranscodeFailedJsonResponse(Exception exception) {
        this.exception = exception;
    }

    @Override
    public JSONObject createResponse() throws JSONException {
        JSONObject failedJson = new JSONObject();
        failedJson.put("progress", 0);
        failedJson.put("completed", false);
        failedJson.put("error", true);
        failedJson.put("message", exception.getMessage());
        return failedJson;
    }
}
class TranscodeProgressJsonResponse implements JsonResponseCreator {
    private final double progress;

    public TranscodeProgressJsonResponse(double progress) {
        this.progress = progress;
    }

    @Override
    public JSONObject createResponse() throws JSONException {
        JSONObject progressJson = new JSONObject();
        progressJson.put("progress", progress);
        progressJson.put("completed", false);
        progressJson.put("error", false);
        return progressJson;
    }
}

interface JsonResponseCreator {
    JSONObject createResponse() throws JSONException;
}
