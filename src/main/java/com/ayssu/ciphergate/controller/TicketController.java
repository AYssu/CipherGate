package com.ayssu.ciphergate.controller;

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
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "工单管理", description = "用户端工单接口")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @Operation(summary = "创建工单")
    public Result<Ticket> createTicket(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        String title = (String) body.get("title");
        String category = (String) body.get("category");
        Integer priority = (Integer) body.getOrDefault("priority", 1);
        String content = (String) body.get("content");
        Ticket ticket = ticketService.createTicket(user.getId(), title, category, priority, content);
        return Result.success(ticket);
    }

    @GetMapping
    @Operation(summary = "我的工单列表")
    public Result<Page<Ticket>> getMyTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(ticketService.getUserTickets(user.getId(), page, size));
    }

    @GetMapping("/{ticketNo}")
    @Operation(summary = "工单详情")
    public Result<Map<String, Object>> getTicketDetail(@PathVariable String ticketNo) {
        Ticket ticket = ticketService.getTicketByNo(ticketNo);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        List<TicketMessage> messages = ticketService.getMessages(ticket.getId());
        return Result.success(Map.of("ticket", ticket, "messages", messages));
    }

    @PostMapping("/{ticketNo}/messages")
    @Operation(summary = "发送消息")
    public Result<TicketMessage> sendMessage(
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
        TicketMessage message = ticketService.sendMessage(ticket.getId(), user.getId(), "USER", content, imageUrls);
        return Result.success(message);
    }

    @PostMapping("/{ticketNo}/close")
    @Operation(summary = "关闭工单")
    public Result<String> closeTicket(@PathVariable String ticketNo, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        Ticket ticket = ticketService.getTicketByNo(ticketNo);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        ticketService.closeTicket(ticket.getId(), user.getId());
        return Result.success("工单已关闭");
    }

    @PostMapping("/{ticketNo}/urge")
    @Operation(summary = "催办工单")
    public Result<String> urgeTicket(@PathVariable String ticketNo, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        Ticket ticket = ticketService.getTicketByNo(ticketNo);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        try {
            ticketService.urgeTicket(ticket.getId(), user.getId());
            return Result.success("催办成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
