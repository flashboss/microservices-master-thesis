package it.valeriovaudi.emarket.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import it.valeriovaudi.emarket.event.model.EventTypeEnum;
import it.valeriovaudi.emarket.event.service.EventDomainPubblishService;
import it.valeriovaudi.emarket.exception.*;
import it.valeriovaudi.emarket.integration.AccountIntegrationService;
import it.valeriovaudi.emarket.integration.ProductCatalogIntegrationService;
import it.valeriovaudi.emarket.model.*;
import it.valeriovaudi.emarket.repository.PurchaseOrderRepository;
import it.valeriovaudi.emarket.security.SecurityUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by mrflick72 on 30/05/17.
 */

@Data
@Slf4j
@Service
public class OnlyRegistratedUserPurchaseOrderService implements PurchaseOrderService {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProductCatalogIntegrationService productCatalogIntegrationService;

    @Autowired
    private AccountIntegrationService accountIntegrationService;

    @Autowired
    private EventDomainPubblishService eventDomainPubblishService;

    @Autowired
    private SecurityUtils securityUtils;

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder findPurchaseOrder(String userName, String orderNumber) {
        try {
            return purchaseOrderRepository.findByUserNameAndOrderNumber(userName, orderNumber).get(2*60, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error(String.format("%s: %s", "error cause message", e.getCause().getMessage()));
            log.error(String.format("%s: %s", "error message", e.getMessage()));
        }
        return null;
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public List<PurchaseOrder> findPurchaseOrderList(boolean withOnlyOrderId) {
        return withOnlyOrderId ? purchaseOrderRepository.findPurchaseOrder().collect(Collectors.toList()) :
                purchaseOrderRepository.findAll();
     }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public List<PurchaseOrder> findPurchaseOrderList(String userName, boolean withOnlyOrderId) {
        return (withOnlyOrderId ? purchaseOrderRepository.findPurchaseOrderIdByUserName(userName) :
                purchaseOrderRepository.findByUserName(userName)).collect(Collectors.toList());
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})

    public PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder) {
        purchaseOrder.setStatus(PurchaseOrderStatusEnum.DRAFT);
        return purchaseOrderRepository.save(purchaseOrder);
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder createPurchaseOrder() {
        String correlationId = UUID.randomUUID().toString();
        String userName = securityUtils.getPrincipalUserName();

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setStatus(PurchaseOrderStatusEnum.DRAFT);
        purchaseOrder.setOrderNumber(UUID.randomUUID().toString());
        purchaseOrder.setOrderDate(new Date());
        purchaseOrder.setUserName(userName);

        return doSavePurchaseOrderData(correlationId, purchaseOrder, SaveGoodsInPurchaseOrderException.class);
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public void deletePurchaseOrder(String orderNumber) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findOne(orderNumber);

        if(PurchaseOrderStatusEnum.DRAFT.equals(purchaseOrder.getStatus()) || purchaseOrder.getStatus()==null){
            purchaseOrderRepository.delete(orderNumber);
        } else {
            eventDomainPubblishService.publishPurchaseOrderErrorEvent(correlationId, orderNumber,
                    null, null, null, EventTypeEnum.DELETE, PurchaseOrderInvalidOperatioOnStatusException.DEFAULT_MESSAGE, PurchaseOrderInvalidOperatioOnStatusException.class);
            throw new PurchaseOrderInvalidOperatioOnStatusException(PurchaseOrderInvalidOperatioOnStatusException.DEFAULT_MESSAGE);
        }

    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder changeStatus(String orderNumber, PurchaseOrderStatusEnum purchaseOrderStatusEnum) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        PurchaseOrder one = purchaseOrderRepository.findOne(orderNumber);
        return purchaseOrderRepository.save(Optional.ofNullable(one)
                .map(purchaseOrder -> {
                    purchaseOrder.setStatus(purchaseOrderStatusEnum);
                    return purchaseOrder;
                }).orElseThrow(RuntimeException::new));
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder withCustomer(String orderNumber, String userName, Customer customer) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        Optional.ofNullable(customer).ifPresent(customerAux -> { throw new UnsupportedOperationException(); });

        PurchaseOrder one = purchaseOrderRepository.findOne(orderNumber);
        Customer customerFormAccountData = accountIntegrationService.getCustomerFormAccountData(userName);
        CustomerContact customerContactFormAccountData = accountIntegrationService.getCustomerContactFormAccountData(userName);

        one.setCustomer(customerFormAccountData);
        one.setCustomerContact(customerContactFormAccountData);

        return  doSavePurchaseOrderData(correlationId, one, SaveCustomerException.class);
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder withCustomerContact(String orderNumber, String userName, CustomerContact customerContact) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        Optional.ofNullable(customerContact).ifPresent(customerAux -> {
            throw new UnsupportedOperationException();
        });

        PurchaseOrder one = purchaseOrderRepository.findOne(orderNumber);
        CustomerContact customerContactFormAccountData = accountIntegrationService.getCustomerContactFormAccountData(userName);
        one.setCustomerContact(customerContactFormAccountData);

        return  doSavePurchaseOrderData(correlationId, one, SaveCustomerContactException.class);
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder withCustomerAndCustomerContact(String orderNumber, String userName, Customer customer, CustomerContact customerContact) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        if (Optional.ofNullable(customer).isPresent() || Optional.ofNullable(customerContact).isPresent()){
            throw new UnsupportedOperationException();
        }

        PurchaseOrder one = purchaseOrderRepository.findOne(orderNumber);

        Customer customerFormAccountData = accountIntegrationService.getCustomerFormAccountData(userName);
        one.setCustomer(customerFormAccountData);

        CustomerContact customerContactFormAccountData = accountIntegrationService.getCustomerContactFormAccountData(userName);
        one.setCustomerContact(customerContactFormAccountData);

        return doSavePurchaseOrderData(correlationId, one, SaveCustomerAndOrCustomerContactException.class);
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder saveGoodsInPurchaseOrder(String orderNumber, String priceListId, String goodsId, int quantity) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        PurchaseOrder one = purchaseOrderRepository.findOne(orderNumber);
        Goods goodsInPriceListData = productCatalogIntegrationService.getGoodsInPriceListData(priceListId, goodsId);
        goodsInPriceListData.setQuantity(quantity);

        List<Goods> goods = Optional.ofNullable(one.getGoodsList()).orElse(new ArrayList<>());
        int indexOf = goods.indexOf(goodsInPriceListData);

        if(indexOf == -1){
            goods.add(goodsInPriceListData);
        } else {
            goods.set(indexOf, goodsInPriceListData);
        }

        one.setGoodsList(goods);

        return doSavePurchaseOrderData(correlationId, one, SaveGoodsInPurchaseOrderException.class);
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder removeGoodsInPurchaseOrder(String orderNumber,String priceListId, String goodsId) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        PurchaseOrder one = purchaseOrderRepository.findOne(orderNumber);
        List<Goods> goods = Optional.ofNullable(one.getGoodsList()).orElse(new ArrayList<>()).stream()
                .filter(goodsAux -> !checkGoods(priceListId,goodsId,goodsAux))
                .collect(Collectors.toList());

        one.setGoodsList(goods);

        return doSavePurchaseOrderData(correlationId, one, SaveGoodsInPurchaseOrderException.class);
    }

    @Override
    public PurchaseOrder withShipment(String orderNumber, Shipment shipment) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        // find the purchase order
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findOne(orderNumber);

        // apply modification
        purchaseOrder.setShipment(shipment);

        // save on mongo
        return doSavePurchaseOrderData(correlationId, purchaseOrder, SaveShipmentException.class);
    }

    @Override
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public PurchaseOrder withDelivery(String orderNumber, Delivery delivery) {
        String correlationId = UUID.randomUUID().toString();
        doCheckPurchaseOrderExist(correlationId, orderNumber);

        // find the purchase order
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findOne(orderNumber);

        // apply modification
        purchaseOrder.setDelivery(delivery);

        // save on mongo
        return doSavePurchaseOrderData(correlationId, purchaseOrder, SaveDeliveryException.class);
    }

    private void doCheckPurchaseOrderExist(String correlationId, String purchaseOrderId) {
        PurchaseOrder purchaseOrder;
        Function<String, PurchaseOrderNotFoundException> f = userNameAux -> {
            eventDomainPubblishService.publishPurchaseOrderErrorEvent(correlationId, purchaseOrderId,
                    null, null, null, EventTypeEnum.READ, PurchaseOrderNotFoundException.DEFAULT_MESSAGE, PurchaseOrderNotFoundException.class);
            return new PurchaseOrderNotFoundException(PurchaseOrderNotFoundException.DEFAULT_MESSAGE);
        };

        try{
            purchaseOrder =  purchaseOrderRepository.findOne(purchaseOrderId);
            if(purchaseOrder== null){
                throw f.apply(correlationId);
            }
        } catch (Exception e){
            throw f.apply(correlationId);
        }
    }

    private PurchaseOrder doSavePurchaseOrderData(String correlationId, PurchaseOrder purchaseOrder, Class<? extends AbstractException> exception) {
        PurchaseOrder purchaseOrderAux;
        AbstractException abstractException = null;
        try{
            purchaseOrderAux = purchaseOrderRepository.save(purchaseOrder);
        } catch (Exception e){
            abstractException = newAbstractException(exception, e);
            eventDomainPubblishService.publishPurchaseOrderErrorEvent(correlationId, purchaseOrder.getOrderNumber(),
                    null,null, purchaseOrder.getUserName(), EventTypeEnum.SAVE, abstractException.getMessage(), exception);
            throw  abstractException;
        }

        return purchaseOrderAux;
    }

    private boolean checkGoods(String priceListId, String goodsId, Goods goods){
        return goods.getId().equals(goodsId) && goods.getPriceListId().equals(priceListId);
    }


    private boolean canDoOperaion(String correlationId, EventTypeEnum eventTypeEnum,Class<? extends Exception> exception, String exceptionMessage, PurchaseOrder purchaseOrder, PurchaseOrderStatusEnum status){
        if(status.equals(purchaseOrder.getStatus())){
            return true;
        } else {
            eventDomainPubblishService.publishPurchaseOrderErrorEvent(correlationId, purchaseOrder.getOrderNumber(),
                    null, null, null, eventTypeEnum, exceptionMessage, exception);
            throw new PurchaseOrderInvalidOperatioOnStatusException(exceptionMessage);
        }
    }

    private AbstractException newAbstractException(Class<? extends AbstractException> exception, Exception e){
        try {
            return (AbstractException) exception.getConstructors()[0].newInstance(e.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}