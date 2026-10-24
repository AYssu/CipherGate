package com.ayssu.ciphergate.dto.transfer;

import lombok.Data;
import java.util.List;

/** 源用户分页筛选；已选 ID 仍受源应用权限和逻辑删除约束。 */
@Data
public class AppUserTransferUserQuery {
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String memberStatus;
    private List<Long> userIds;
}
