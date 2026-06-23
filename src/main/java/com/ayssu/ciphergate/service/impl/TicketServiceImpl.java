package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.Ticket;
import com.ayssu.ciphergate.entity.TicketMessage;
import com.ayssu.ciphergate.entity.TicketUrge;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.TicketMapper;
import com.ayssu.ciphergate.mapper.TicketMessageMapper;
import com.ayssu.ciphergate.mapper.TicketUrgeMapper;
import com.ayssu.ciphergate.service.TicketService;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.service.SystemMessageService;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketMessageMapper ticketMessageMapper;

    @Autowired
    private TicketUrgeMapper ticketUrgeMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemMessageService systemMessageService;

    @Autowired
    private SystemSmtpMailService mailService;

    @Override
    @Transactional
    public Ticket createTicket(Long userId, String title, String category, Integer priority, String content) {
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setUserId(userId);
        ticket.setTitle(title);
        ticket.setCategory(category);
        ticket.setPriority(priority);
        ticket.setStatus(0);
        save(ticket);

        TicketMessage message = new TicketMessage();
        message.setTicketId(ticket.getId());
        message.setSenderId(userId);
        message.setSenderType("USER");
        message.setContent(content);
        message.setIsRead(false);
        ticketMessageMapper.insert(message);

        ticket.setLastReplyUserId(userId);
        ticket.setLastReplyAt(LocalDateTime.now());
        updateById(ticket);

        // 通知管理员
        notifyAdminsNewTicket(ticket, title, content, priority);

        log.info("工单创建成功: ticketNo={}, userId={}", ticket.getTicketNo(), userId);
        return ticket;
    }

    private void notifyAdminsNewTicket(Ticket ticket, String title, String content, Integer priority) {
        try {
            // 获取所有管理员用户
            List<User> admins = userService.getUsersByRole("ADMIN");
            List<User> superAdmins = userService.getUsersByRole("SUPER_ADMIN");
            admins.addAll(superAdmins);

            if (admins.isEmpty()) {
                log.warn("没有找到管理员用户，无法发送工单通知");
                return;
            }

            // 构建通知内容
            String priorityText = switch (priority != null ? priority : 1) {
                case 2 -> "【重要】";
                case 3 -> "【紧急】";
                default -> "";
            };
            String msgContent = String.format("%s新工单: %s\n工单号: %s\n内容: %s",
                    priorityText, title, ticket.getTicketNo(),
                    content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);

            // 发送系统消息给每个管理员
            for (User admin : admins) {
                systemMessageService.createMessage(
                        "TICKET_NOTIFICATION",
                        "新工单通知 - " + title,
                        msgContent,
                        priority != null && priority >= 2 ? "URGENT" : "NORMAL",
                        "USER",
                        admin.getId()
                );
            }

            // 重要/紧急工单发送邮件通知
            if (priority != null && priority >= 2) {
                String priorityLabel = priority == 3 ? "紧急" : "重要";
                String emailSubject = String.format("[工单通知] %s工单: %s", priorityLabel, title);
                String emailContent = String.format(
                        "您收到一个新的%s工单：\n\n" +
                        "工单号：%s\n" +
                        "标题：%s\n" +
                        "内容：%s\n\n" +
                        "请及时处理。",
                        priorityLabel,
                        ticket.getTicketNo(),
                        title,
                        content
                );

                for (User admin : admins) {
                    if (admin.getEmail() != null && !admin.getEmail().isEmpty()) {
                        try {
                            mailService.sendPlainText(admin.getEmail(), emailSubject, emailContent);
                            log.info("工单邮件通知已发送: admin={}, ticketNo={}", admin.getLogin(), ticket.getTicketNo());
                        } catch (Exception e) {
                            log.error("发送工单邮件通知失败: admin={}, error={}", admin.getLogin(), e.getMessage());
                        }
                    }
                }
            }

            log.info("工单通知已发送: ticketNo={}, adminCount={}", ticket.getTicketNo(), admins.size());
        } catch (Exception e) {
            log.error("发送工单通知失败: ticketNo={}, error={}", ticket.getTicketNo(), e.getMessage());
        }
    }

    @Override
    public Page<Ticket> getUserTickets(Long userId, int page, int size) {
        return lambdaQuery()
                .eq(Ticket::getUserId, userId)
                .orderByDesc(Ticket::getCreatedAt)
                .page(new Page<>(page, size));
    }

    @Override
    public Page<Ticket> getAllTickets(int page, int size, Integer status, Integer priority) {
        var query = lambdaQuery();
        if (status != null) {
            query.eq(Ticket::getStatus, status);
        }
        if (priority != null) {
            query.eq(Ticket::getPriority, priority);
        }
        return query.orderByDesc(Ticket::getCreatedAt).page(new Page<>(page, size));
    }

    @Override
    public Ticket getTicketByNo(String ticketNo) {
        return lambdaQuery().eq(Ticket::getTicketNo, ticketNo).one();
    }

    @Override
    public List<TicketMessage> getMessages(Long ticketId) {
        return ticketMessageMapper.selectList(
                new QueryWrapper<TicketMessage>()
                        .eq("ticket_id", ticketId)
                        .orderByAsc("created_at"));
    }

    @Override
    @Transactional
    public TicketMessage sendMessage(Long ticketId, Long senderId, String senderType, String content, String imageUrls) {
        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setContent(content);
        message.setImageUrls(imageUrls);
        message.setIsRead(false);
        ticketMessageMapper.insert(message);

        Ticket ticket = getById(ticketId);
        if (ticket != null) {
            ticket.setLastReplyUserId(senderId);
            ticket.setLastReplyAt(LocalDateTime.now());
            if ("ADMIN".equals(senderType) && ticket.getStatus() == 0) {
                ticket.setStatus(1);
            }
            if ("USER".equals(senderType) && ticket.getStatus() == 1) {
                ticket.setStatus(2);
            }
            updateById(ticket);
        }

        return message;
    }

    @Override
    @Transactional
    public void closeTicket(Long ticketId, Long userId) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        ticket.setStatus(4);
        updateById(ticket);
    }

    @Override
    @Transactional
    public void urgeTicket(Long ticketId, Long userId) {
        String redisKey = "ticket:urge:" + ticketId + ":" + userId;
        Boolean exists = stringRedisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            throw new RuntimeException("催办过于频繁，请1小时后再试");
        }

        TicketUrge urge = new TicketUrge();
        urge.setTicketId(ticketId);
        urge.setUserId(userId);
        ticketUrgeMapper.insert(urge);

        stringRedisTemplate.opsForValue().set(redisKey, "1", 1, TimeUnit.HOURS);

        log.info("工单催办: ticketId={}, userId={}", ticketId, userId);
    }

    @Override
    @Transactional
    public void assignTicket(Long ticketId, Long adminId) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        ticket.setAssignedTo(adminId);
        ticket.setStatus(1);
        updateById(ticket);
    }

    @Override
    @Transactional
    public void updateStatus(Long ticketId, Integer status) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        ticket.setStatus(status);
        updateById(ticket);
    }

    @Override
    @Transactional
    public void updateStatusWithNotify(Long ticketId, Integer status, boolean sendEmail, String remark) {
        Ticket ticket = getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }

        Integer oldStatus = ticket.getStatus();
        ticket.setStatus(status);
        updateById(ticket);

        // 状态变更为已解决(3)或已关闭(4)时通知用户
        if (status != null && (status == 3 || status == 4) && ticket.getUserId() != null) {
            notifyUserTicketResolved(ticket, status, sendEmail, remark);
        }
    }

    private void notifyUserTicketResolved(Ticket ticket, Integer status, boolean sendEmail, String remark) {
        try {
            User ticketUser = userService.getById(ticket.getUserId());
            if (ticketUser == null) {
                log.warn("工单用户不存在: userId={}", ticket.getUserId());
                return;
            }

            String statusText = status == 3 ? "已解决" : "已关闭";
            String msgContent = String.format("您的工单 %s：%s\n工单号：%s",
                    statusText, ticket.getTitle(), ticket.getTicketNo());
            if (remark != null && !remark.isEmpty()) {
                msgContent += "\n处理备注：" + remark;
            }

            // 发送系统消息给用户
            systemMessageService.createMessage(
                    "TICKET_RESOLVED",
                    "工单" + statusText + " - " + ticket.getTitle(),
                    msgContent,
                    "NORMAL",
                    "USER",
                    ticketUser.getId()
            );

            // 如果管理员选择发送邮件
            if (sendEmail && ticketUser.getEmail() != null && !ticketUser.getEmail().isEmpty()) {
                String emailSubject = String.format("[工单通知] 您的工单%s：%s", statusText, ticket.getTitle());
                String emailContent = String.format(
                        "您好 %s，\n\n" +
                        "您的工单已%s：\n\n" +
                        "工单号：%s\n" +
                        "标题：%s\n",
                        ticketUser.getName() != null ? ticketUser.getName() : ticketUser.getLogin(),
                        statusText,
                        ticket.getTicketNo(),
                        ticket.getTitle()
                );
                if (remark != null && !remark.isEmpty()) {
                    emailContent += "处理备注：" + remark + "\n";
                }
                emailContent += "\n如有问题请随时联系我们。\n\n此致";

                try {
                    mailService.sendPlainText(ticketUser.getEmail(), emailSubject, emailContent);
                    log.info("工单解决邮件通知已发送: user={}, ticketNo={}", ticketUser.getLogin(), ticket.getTicketNo());
                } catch (Exception e) {
                    log.error("发送工单解决邮件失败: user={}, error={}", ticketUser.getLogin(), e.getMessage());
                }
            }

            log.info("工单状态通知已发送: ticketNo={}, status={}, userId={}", ticket.getTicketNo(), status, ticket.getUserId());
        } catch (Exception e) {
            log.error("发送工单状态通知失败: ticketNo={}, error={}", ticket.getTicketNo(), e.getMessage());
        }
    }

    private String generateTicketNo() {
        return "TK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
