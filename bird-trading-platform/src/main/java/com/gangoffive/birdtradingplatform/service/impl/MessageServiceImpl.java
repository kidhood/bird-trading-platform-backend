package com.gangoffive.birdtradingplatform.service.impl;

import com.gangoffive.birdtradingplatform.api.response.ErrorResponse;
import com.gangoffive.birdtradingplatform.common.KafkaConstant;
import com.gangoffive.birdtradingplatform.common.MessageConstant;
import com.gangoffive.birdtradingplatform.common.PagingAndSorting;
import com.gangoffive.birdtradingplatform.dto.MessageDto;
import com.gangoffive.birdtradingplatform.entity.Account;
import com.gangoffive.birdtradingplatform.entity.Channel;
import com.gangoffive.birdtradingplatform.entity.Message;
import com.gangoffive.birdtradingplatform.enums.MessageStatus;
import com.gangoffive.birdtradingplatform.exception.CustomRuntimeException;
import com.gangoffive.birdtradingplatform.mapper.MessageMapper;
import com.gangoffive.birdtradingplatform.repository.AccountRepository;
import com.gangoffive.birdtradingplatform.repository.ChannelRepository;
import com.gangoffive.birdtradingplatform.repository.MessageRepository;
import com.gangoffive.birdtradingplatform.repository.ShopOwnerRepository;
import com.gangoffive.birdtradingplatform.service.ChannelService;
import com.gangoffive.birdtradingplatform.service.MessageService;
import com.gangoffive.birdtradingplatform.util.JsonUtil;
import com.google.gson.Gson;
import com.gangoffive.birdtradingplatform.wrapper.PageNumberWrapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final AccountRepository accountRepository;
    private final ShopOwnerRepository shopOwnerRepository;
    private final ChannelService channelService;
    private final ChannelRepository channelRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    @Override
    public boolean saveMessage(Message message) {
        try{
            messageRepository.save(message);
        }catch (Exception e){
            return false;
        }
        return true;
    }

    @Override
    public ResponseEntity<?> getListMessageByChannelId(long channelId, int pageNumber, long id, boolean isShop) {
        PageRequest pageRequest = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_MESSAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp"));
        var listMessage = messageRepository.findByChannel_Id(channelId, pageRequest);
        if(listMessage != null) {
            PageNumberWrapper pageNumberWrapper = new PageNumberWrapper();
            List<MessageDto> result = new ArrayList<>();
            if(isShop) {
                var shopOwner = shopOwnerRepository.findById(id);
                if(shopOwner.isPresent()) {
                    long accountId = shopOwner.get().getAccount().getId();
                    result = listMessage.getContent().stream()
                            .map(message -> this.messageToDto(message, accountId)).toList();
                }
            }else{
                result = listMessage.getContent().stream()
                        .map(message -> this.messageToDto(message, id)).toList();
            }

            List<MessageDto> reversedList = new ArrayList<>(result);
            Collections.reverse(reversedList);
            pageNumberWrapper.setLists(reversedList);
            pageNumberWrapper.setPageNumber(listMessage.getTotalPages());
            return ResponseEntity.ok(pageNumberWrapper);
        }
        return null;
    }

    private MessageDto messageToDto (Message message, long id) {
        //only other message change to seen
        if(message.getAccount().getId() != id) {
            message.setStatus(MessageStatus.SEEN);
            //save all to read
            messageRepository.save(message);
        }
        return messageMapper.modelToDto(message);
    }

    @Override
    public boolean maskAllSeen(long senderId, long channelId) {
        log.info(String.format("Here is sender id %d channelid %d", senderId, channelId));
        try{
            messageRepository.updateStatusToSeen(MessageStatus.SEEN.name(),channelId, senderId,MessageStatus.SENT.name());
        }catch (Exception e) {
            throw new CustomRuntimeException("400", "Something went wrong");
        }

        return false;
    }

    @Override
    public String getListUserInChannel(int pageNumber, long shopId) {
        try {
            PageRequest page = PageRequest.of(pageNumber, MessageConstant.CHANNEL_PAGING_SIZE,
                    Sort.by(Sort.Direction.DESC, "lastedUpdate"));
            Page<Channel> channels = channelRepository.findByShopOwner_Id(shopId, page);
            List<JsonObject> result = channels.getContent().stream()
                    .map(a -> this.createUserListWithUnread(a, shopId)).toList();
            return this.createPageNumberWarrpper(result, channels.getTotalPages()).toString();
        }catch (Exception e) {
            throw new CustomRuntimeException("400", "Have no channel");
        }
    }

    @Override
    public ResponseEntity<?> getTotalNumberUnreadMessageUser(long userid) {
        var acc = accountRepository.findById(userid);
        if(acc.isPresent()) {
            List<Channel> channelList = acc.get().getChannels();
            Long totalUnread = messageRepository.countByAccount_IdNotInAndStatusInAndChannelIn(Arrays.asList(userid), MessageConstant.STATUS_UNREAD, channelList);
            JsonObject numberUnread = new JsonObject();
            if(totalUnread != null){
                numberUnread.addProperty("totalUnread", totalUnread);
            }else {
                numberUnread.addProperty("totalUnread", 0);
            }
            return ResponseEntity.ok(numberUnread.toString());
        }else {
            return new ResponseEntity<>(ErrorResponse.builder().errorCode("400").errorMessage("This shop have no shop").build(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<?> getTotalNumberUnreadMessageShop(long shopid) {
        var shop  = shopOwnerRepository.findById(shopid);
        if(shop.isPresent()){
            long accountID = shop.get().getAccount().getId();
            Long totalUnread = messageRepository.countByAccount_IdNotInAndStatusInAndChannelIn(Arrays.asList(accountID),
                    MessageConstant.STATUS_UNREAD, shop.get().getChannels());
            JsonObject numberUnread = new JsonObject();
            if(totalUnread != null){
                numberUnread.addProperty("totalUnread", totalUnread);
            }else {
                numberUnread.addProperty("totalUnread", 0);
            }
            return ResponseEntity.ok(numberUnread.toString());
        }else{
            return new ResponseEntity<>(ErrorResponse.builder().errorCode("400").errorMessage("This shop have no shop").build(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<?> handleSendMessage(MessageDto message) {
        long userID = message.getUserID();
        long shopId = message.getShopID();
        long senderId = 0;
        long receiveId = 0;
        saveMessage(message);
        message.setId(System.currentTimeMillis());
        if(message.getSenderName().equalsIgnoreCase(MessageConstant.MESSAGE_SHOP_ROLE)) {
            receiveId = userID;
//            //Set user id is id of account shop owner
//            message.setUserID(senderId);
            //check user cannot send to their shop
            if(senderId == receiveId) {
                throw new CustomRuntimeException("400", "You cannot send message for your shop!");
            }
            this.sendMessage(message);
        }else if (message.getSenderName().equalsIgnoreCase(MessageConstant.MESSAGE_USER_ROLE)) {
            senderId = userID;
            long accountShopId = shopOwnerRepository.findById(shopId).get().getAccount().getId();
            Account acc =  accountRepository.findById(senderId).get();
            //check user cannot send to their shop
            if(senderId == accountShopId) {
                throw new CustomRuntimeException("400", "You cannot send message for your shop!");
            }
            message.setUserAvatar(acc.getImgUrl());
            message.setUserName(acc.getFullName());
            this.sendMessage(message);
        } else {
            throw new CustomRuntimeException("400","Sender name not correct!");
        }
        return ResponseEntity.ok("OKe");
    }
    @Async
    void sendMessage(MessageDto messageDto) {
        String message = JsonUtil.INSTANCE.getJsonString(messageDto);
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KafkaConstant.KAFKA_PRIVATE_CHAT, message);
        try  {
            SendResult<String, String> response = future.get();
            log.info("Record metadata: {}", response.getRecordMetadata());
        }catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void saveMessage(MessageDto message) {
        long senderId = 0;
        if (message.getSenderName().equalsIgnoreCase(MessageConstant.MESSAGE_SHOP_ROLE)) {
            senderId = shopOwnerRepository.findById(message.getShopID()).get().getAccount().getId();
        }else{
            senderId = message.getUserID();
        }
        //save to database
        Channel channel = channelService.getAndSaveChannel(message.getUserID(),message.getShopID());
        Message messTemp = messageMapper.dtoToModle(message);
        //set channel
        messTemp.setChannel(channel);
        //set account
        Account acc = new Account();
        acc.setId(senderId);
        messTemp.setAccount(acc);
        channelService.setLastedUpdateTime(channel.getId());
        //save message
        messageRepository.save(messTemp);
        //update time of channel id

        //mask all other message to read;
        maskAllSeen(senderId,channel.getId());
        log.info(String.format("Message like %s",message.toString()));
    }

    private JsonObject createUserListWithUnread(Channel channel, long shopId) {
        if(channel != null) {
            long userId = channel.getAccount().getId();
            long channelId = channel.getId();
            String userAvatar = channel.getAccount().getImgUrl();
            String name = channel.getAccount().getFullName();
            int unread  = channelService.getMessageUnreadByUserAndShopForShopOwner(userId, shopId);
            //create an json object
            JsonObject result = new JsonObject();
            result.addProperty("userId", userId);
            result.addProperty("channelId", channelId);
            result.addProperty("userName", name);
            result.addProperty("userAvatar", userAvatar);
            result.addProperty("unread", unread);
            return result;
        }
        return null;
    }

    private JsonObject createPageNumberWarrpper (List<JsonObject> jsonObject, int pageNumber) {
        Gson gson = new Gson();
        String listJson = gson.toJson(jsonObject);
        JsonObject warrpperJson = new JsonObject();
        warrpperJson.add("lists", JsonParser.parseString(listJson).getAsJsonArray());
        warrpperJson.addProperty("pageNumber", pageNumber);
        return warrpperJson;
    }
}
