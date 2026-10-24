package com.ayssu.ciphergate.dto.transfer;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class AppUserTransferRequest {
    public enum Mode { MOVE, COPY }
    @NotNull @Positive private Long sourceAppId;
    @NotNull @Positive private Long targetAppId;
    @NotNull private Mode mode;
    private boolean forceOverwrite;
    @NotEmpty @Size(max = 100) private List<@NotNull @Positive Long> userIds;
    /** 必须先预检查；执行时重新计算快照，拒绝过期或重复执行。 */
    private String previewToken;
}
