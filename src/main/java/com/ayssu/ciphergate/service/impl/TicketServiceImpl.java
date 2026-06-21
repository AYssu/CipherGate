package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.Ticket;
import com.ayssu.ciphergate.entity.TicketMessage;
import com.ayssu.ciphergate.entity.TicketUrge;
import com.ayssu.ciphergate.mapper.TicketMapper;
import com.ayssu.ciphergate.mapper.TicketMessageMapper;
import com.ayssu.ciphergate.mapper.TicketUrgeMapper;
import com.ayssu.ciphergate.service.TicketService;
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

        log.info("工单创建成功: ticketNo={}, userId={}", ticket.getTicketNo(), userId);
        return ticket;
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

    private String generateTicketNo() {
        return "TK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
