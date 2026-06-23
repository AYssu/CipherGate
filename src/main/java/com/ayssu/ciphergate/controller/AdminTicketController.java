package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Ticket;
import com.ayssu.ciphergate.entity.TicketMessage;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.TicketService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@Tag(name = "管理员工单管理", description = "管理员端工单接口")
public class AdminTicketController {

    private final TicketService ticketService;

    @GetMapping
    @RequirePermission("TICKET_ADMIN_LIST")
    @Operation(summary = "管理员查看所有工单")
    public Result<Page<Ticket>> getAllTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority) {
        return Result.success(ticketService.getAllTickets(page, size, status, priority));
    }

    @GetMapping("/{ticketNo}")
    @RequirePermission("TICKET_ADMIN_DETAIL")
    @Operation(summary = "管理员查看工单详情")
    public Result<Map<String, Object>> getTicketDetail(@PathVariable String ticketNo) {
        Ticket ticket = ticketService.getTicketByNo(ticketNo);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        List<TicketMessage> messages = ticketService.getMessages(ticket.getId());
        return Result.success(Map.of("ticket", ticket, "messages", messages));
    }

    @PutMapping("/{ticketNo}/assign")
    @RequirePermission("TICKET_ADMIN_ASSIGN")
    @Operation(summary = "管理员分配工单")
    public Result<String> assignTicket(
            @PathVariable String ticketNo,
            @RequestBody Map<String, Long> body) {
        Ticket ticket = ticketService.getTicketByNo(ticketNo);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        Long adminId = body.get("adminId");
        ticketService.assignTicket(ticket.getId(), adminId);
        return Result.success("分配成功");
    }

    @PostMapping("/{ticketNo}/messages")
    @RequirePermission("TICKET_ADMIN_REPLY")
    @Operation(summary = "管理员回复")
    public Result<TicketMessage> replyTicket(
            @PathVariable String ticketNo,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        Ticket ticket = ticketService.getTicketByNo(ticketNo);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        String content = body.get("content");
        String imageUrls = body.get("imageUrls");
        TicketMessage message = ticketService.sendMessage(ticket.getId(), user.getId(), "ADMIN", content, imageUrls);

        // 如果是"补充信息请求"，将工单状态改为"等待回复"
        if ("true".equals(body.get("requestInfo"))) {
            ticketService.updateStatus(ticket.getId(), 2);
        }

        return Result.success(message);
    }

    @PutMapping("/{ticketNo}/status")
    @RequirePermission("TICKET_ADMIN_UPDATE_STATUS")
    @Operation(summary = "管理员更新工单状态")
    public Result<String> updateStatus(
            @PathVariable String ticketNo,
            @RequestBody Map<String, Object> body) {
        Ticket ticket = ticketService.getTicketByNo(ticketNo);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        Integer status = (Integer) body.get("status");
        Boolean sendEmail = body.get("sendEmail") != null ? (Boolean) body.get("sendEmail") : false;
        String remark = (String) body.get("remark");

        if (sendEmail != null && sendEmail) {
            ticketService.updateStatusWithNotify(ticket.getId(), status, true, remark);
        } else {
            ticketService.updateStatus(ticket.getId(), status);
        }
        return Result.success("状态更新成功");
    }
}
