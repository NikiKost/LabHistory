package ru.labhistory.app.ocr;

public final class DocumentRecognitionResult {
    public final String text;
    public final Long detectedAnalysisDate;

    DocumentRecognitionResult(String text, Long detectedAnalysisDate) {
        this.text = text == null ? "" : text;
        this.detectedAnalysisDate = detectedAnalysisDate;
    }
}
