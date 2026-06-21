package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.Ticket;
import com.ayssu.ciphergate.entity.TicketMessage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface TicketService extends IService<Ticket> {

    Ticket createTicket(Long userId, String title, String category, Integer priority, String content);

    Page<Ticket> getUserTickets(Long userId, int page, int size);

    Page<Ticket> getAllTickets(int page, int size, Integer status, Integer priority);

    Ticket getTicketByNo(String ticketNo);

    List<TicketMessage> getMessages(Long ticketId);

    TicketMessage sendMessage(Long ticketId, Long senderId, String senderType, String content, String imageUrls);

    void closeTicket(Long ticketId, Long userId);

    void urgeTicket(Long ticketId, Long userId);

    void assignTicket(Long ticketId, Long adminId);

    void updateStatus(Long ticketId, Integer status);
}
