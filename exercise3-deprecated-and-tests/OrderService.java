package com.practice.orders;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

/**
 * PR #507 — "Order placement for ETF savings plans"
 *
 * Places a buy order for a customer's savings plan execution day and notifies
 * downstream listeners. Review it as you would on GitHub. ~10 minutes.
 */
@Service
public class OrderService extends Observable {

    private static final Logger log = Logger.getLogger("OrderService");

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PositionRepository positionRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    private String marketDataUrl = "http://market-data.internal/prices/";

    public Order placeOrder(String customerId, String isin, Float amountEur) throws Exception {
        if (amountEur == 0.0f) {
            throw new Exception("amount must not be zero");
        }

        Float price = restTemplate.getForObject(new URL(marketDataUrl + isin).toString(), Float.class);
        Float units = amountEur / price;

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setIsin(isin);
        order.setAmount(amountEur);
        order.setUnits(units);
        order.setStatus("NEW");
        order.setExecutionDate(DATE_FORMAT.format(new Date()));
        order.setCreatedAt(new Integer((int) (System.currentTimeMillis() / 1000)));

        saveAndUpdatePosition(order);

        setChanged();
        notifyObservers(order);

        sendConfirmation(order);
        return order;
    }

    @Transactional
    private void saveAndUpdatePosition(Order order) {
        orderRepository.save(order);
        Position pos = positionRepository.findByCustomerIdAndIsin(order.getCustomerId(), order.getIsin());
        if (pos == null) {
            pos = new Position(order.getCustomerId(), order.getIsin(), 0f);
        }
        pos.setUnits(pos.getUnits() + order.getUnits());
        positionRepository.save(pos);
    }

    @Async
    public void sendConfirmation(Order order) {
        try {
            Thread.sleep(200);
            log.info("Confirmation sent for " + order.getId());
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public void cancelAll(String customerId) {
        for (Order o : orderRepository.findByCustomerId(customerId)) {
            if (o.getStatus() == "NEW") {
                o.setStatus("CANCELLED");
                orderRepository.save(o);
            }
        }
    }

    public void registerListener(Observer observer) {
        addObserver(observer);
    }
}
