package com.ayssu.ciphergate.dto;

import lombok.Data;

import java.util.List;

@Data
public class LicenseImportResult {
    private int totalRows;
    private int successCount;
    private int failCount;
    private List<FailItem> failItems;

    @Data
    public static class FailItem {
        private int rowNumber;
        private String keyCode;
        private String reason;

        public FailItem(int rowNumber, String keyCode, String reason) {
            this.rowNumber = rowNumber;
            this.keyCode = keyCode;
            this.reason = reason;
        }
    }
}
