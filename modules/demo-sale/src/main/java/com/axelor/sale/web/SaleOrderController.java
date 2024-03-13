/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2022 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.sale.web;

import com.axelor.common.ObjectUtils;
import com.axelor.db.JpaSupport;
import com.axelor.db.mapper.Mapper;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.sale.db.Order;
import com.axelor.sale.db.OrderLine;
import com.axelor.sale.db.OrderStatus;
import com.axelor.sale.db.repo.OrderLineRepository;
import com.axelor.sale.service.SaleOrderService;
import com.google.common.collect.Lists;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import org.apache.commons.collections.CollectionUtils;

public class SaleOrderController extends JpaSupport {

  @Inject private SaleOrderService service;

  private static final String TYPE_CHILDREN = "children";
  private static final String TYPE_PARENT = "parent";

  public void onConfirm(ActionRequest request, ActionResponse response) {

    Order order = request.getContext().asType(Order.class);

    response.setReadonly("orderDate", order.getConfirmed());
    response.setReadonly("confirmDate", order.getConfirmed());

    if (order.getConfirmed() == Boolean.TRUE && order.getConfirmDate() == null) {
      response.setValue("confirmDate", LocalDate.now());
    }

    if (order.getConfirmed() == Boolean.TRUE) {
      response.setValue("status", OrderStatus.OPEN);
    } else if (order.getStatus() == OrderStatus.OPEN) {
      response.setValue("status", OrderStatus.DRAFT);
    }
  }

  public void calculate(ActionRequest request, ActionResponse response) {

    Order order = request.getContext().asType(Order.class);
    order = service.calculate(order);

    response.setValue("amount", order.getAmount());
    response.setValue("taxAmount", order.getTaxAmount());
    response.setValue("totalAmount", order.getTotalAmount());
  }

  public void reportToday(ActionRequest request, ActionResponse response) {
    EntityManager em = getEntityManager();
    Query q1 =
        em.createQuery(
            "SELECT SUM(self.totalAmount) FROM Order AS self "
                + "WHERE YEAR(self.orderDate) = YEAR(current_date) AND "
                + "MONTH(self.orderDate) = MONTH(current_date) AND "
                + "DAY(self.orderDate) = DAY(current_date) - 1");

    Query q2 =
        em.createQuery(
            "SELECT SUM(self.totalAmount) FROM Order AS self "
                + "WHERE YEAR(self.orderDate) = YEAR(current_date) AND "
                + "MONTH(self.orderDate) = MONTH(current_date) AND "
                + "DAY(self.orderDate) = DAY(current_date)");

    List<?> r1 = q1.getResultList();
    BigDecimal last = r1.get(0) == null ? BigDecimal.ZERO : (BigDecimal) r1.get(0);

    List<?> r2 = q2.getResultList();
    BigDecimal total = r2.get(0) == null ? BigDecimal.ZERO : (BigDecimal) r2.get(0);

    BigDecimal percent = BigDecimal.ZERO;
    if (total.compareTo(BigDecimal.ZERO) == 1) {
      percent =
          total.subtract(last).multiply(new BigDecimal(100)).divide(total, RoundingMode.HALF_UP);
    }

    Map<String, Object> data = new HashMap<>();
    data.put("total", total);
    data.put("percent", percent);
    data.put("down", total.compareTo(last) == -1);

    response.setData(Lists.newArrayList(data));
  }

  public void reportMonthly(ActionRequest request, ActionResponse response) {
    EntityManager em = getEntityManager();
    Query q1 =
        em.createQuery(
            "SELECT SUM(self.totalAmount) FROM Order AS self "
                + "WHERE YEAR(self.orderDate) = YEAR(current_date) AND "
                + "MONTH(self.orderDate) = MONTH(current_date) - 1");

    Query q2 =
        em.createQuery(
            "SELECT SUM(self.totalAmount) FROM Order AS self "
                + "WHERE YEAR(self.orderDate) = YEAR(current_date) AND "
                + "MONTH(self.orderDate) = MONTH(current_date)");

    List<?> r1 = q1.getResultList();
    BigDecimal last = r1.get(0) == null ? BigDecimal.ZERO : (BigDecimal) r1.get(0);

    List<?> r2 = q2.getResultList();
    BigDecimal total = r2.get(0) == null ? BigDecimal.ZERO : (BigDecimal) r2.get(0);

    BigDecimal percent = BigDecimal.ZERO;
    if (total.compareTo(BigDecimal.ZERO) == 1) {
      percent = total.subtract(last).divide(total, 4, RoundingMode.HALF_UP);
    }

    Map<String, Object> data = new HashMap<>();
    data.put("total", total);
    data.put("percent", percent);
    data.put("up", total.compareTo(last) > 0);
    data.put("tag", I18n.get("Monthly"));
    data.put("tagCss", "label-success");

    response.setData(Lists.newArrayList(data));
  }

  public void showTotalSales(ActionRequest request, ActionResponse response) {
    List<Map<String, Object>> data =
        (List<Map<String, Object>>) request.getRawContext().get("_data");
    if (ObjectUtils.isEmpty(data)) {
      response.setNotify(I18n.get("No sales"));
      return;
    }
    BigDecimal totalAmount =
        data.stream()
            .map(i -> i.get("amount").toString())
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    response.setNotify(String.format("%s : %s", I18n.get("Total sales"), totalAmount));
  }

  public void showCustomerSales(ActionRequest request, ActionResponse response) {
    Object data = request.getRawContext().get("customerId");
    if (ObjectUtils.isEmpty(data)) {
      return;
    }

    ActionView.ActionViewBuilder builder =
        ActionView.define("Customer sales").model(Order.class.getName());
    builder.domain("self.customer.id = " + data);
    response.setView(builder.map());
  }

  @SuppressWarnings("unchecked")
  public void onLineChange(ActionRequest request, ActionResponse response) {
    Map<String, Object> context = request.getRawContext();
    OrderLine dirtyLine = getDirtyLine((List<Map<String, Object>>) context.get("items"));
    if (dirtyLine == null) {
      return;
    }
    Order order = request.getContext().asType(Order.class);
    List<OrderLine> items = order.getItems();
    if (items == null) {
      return;
    }
    updateChild(dirtyLine);
    replaceDirtyLineInItems(dirtyLine, items);
    updateParent(dirtyLine, items);

    response.setValue(
        "items", items.stream().map(this::toMapWithSubLine).collect(Collectors.toList()));
  }

  private void print(List<OrderLine> items) {
    if (items == null) {
      return;
    }
    for (OrderLine orderLine : items) {
      System.err.println(orderLine);
      print(orderLine.getItems());
    }
  }

  private boolean replaceDirtyLineInItems(OrderLine dirtyLine, List<OrderLine> items) {
    if (items == null) {
      return false;
    }

    int i = 0;
    for (OrderLine orderLine : items) {
      if (isEqual(orderLine, dirtyLine)) {
        items.set(i, dirtyLine);
        return true;
      }
      if (orderLine.getItems() != null
          && replaceDirtyLineInItems(dirtyLine, orderLine.getItems())) {
        return true;
      }
      i++;
    }
    return false;
  }

  private void updateChild(OrderLine line) {
    List<OrderLine> items = line.getItems();
    BigDecimal qtyCoef = getQtyCoef(line);
    BigDecimal priceCoef = getPriceCoef(line);
    if (ObjectUtils.isEmpty(items)
        || (BigDecimal.ONE.compareTo(qtyCoef) == 0 && BigDecimal.ONE.compareTo(priceCoef) == 0)) {
      return;
    }
    for (OrderLine orderLine : items) {
      updateValues(orderLine, qtyCoef, priceCoef);
    }
  }

  protected BigDecimal getPriceCoef(OrderLine line) {
    BigDecimal oldPrice = line.getOldPrice();
    if (oldPrice.signum() == 0) {
      return BigDecimal.ONE;
    }
    return line.getPrice().divide(oldPrice, 4, RoundingMode.HALF_EVEN);
  }

  protected BigDecimal getQtyCoef(OrderLine line) {
    if (line.getOldQty() == 0) {
      return BigDecimal.ONE;
    }
    return BigDecimal.valueOf(line.getQuantity())
        .divide(BigDecimal.valueOf(line.getOldQty()), 4, RoundingMode.HALF_EVEN);
  }

  private void updateParent(OrderLine dirtyLine, List<OrderLine> list) {
    for (OrderLine orderLine : list) {
      if (isOrHasDirtyLine(orderLine, dirtyLine)) {
        compute(orderLine);
      }
    }
  }

  private void compute(OrderLine orderLine) {
    List<OrderLine> items = orderLine.getItems();
    if (ObjectUtils.isEmpty(items)) {
      return;
    }
    Integer quantity = orderLine.getQuantity();
    BigDecimal totalPrice = BigDecimal.ZERO;
    for (OrderLine line : items) {
      compute(line);
      totalPrice = totalPrice.add(line.getTotalPrice());
    }
    totalPrice = quantity == 0 ? BigDecimal.ZERO : totalPrice;
    BigDecimal price =
        quantity == 0
            ? BigDecimal.ZERO
            : totalPrice.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_EVEN);
    orderLine.setPrice(price);
    orderLine.setTotalPrice(totalPrice);
  }

  private boolean isOrHasDirtyLine(OrderLine orderLine, OrderLine dirtyLine) {

    if (isEqual(orderLine, dirtyLine)) {
      return true;
    }
    if (orderLine.getItems() == null) {
      return false;
    }

    List<OrderLine> items = orderLine.getItems();
    if (orderLine.getId() != null && items == null) {
      List<OrderLine> list =
          Beans.get(OrderLineRepository.class).find(orderLine.getId()).getItems();
      if (list != null) {
        items = list;
      }
    }

    for (OrderLine line : items) {
      if (isOrHasDirtyLine(line, dirtyLine)) {
        return true;
      }
    }
    return false;
  }

  private void updateValues(OrderLine orderLine, BigDecimal qtyCoef, BigDecimal priceCoef) {
    updateQty(orderLine, qtyCoef);
    updatePrice(orderLine, priceCoef);
    orderLine.setTotalPrice(
        BigDecimal.valueOf(orderLine.getQuantity()).multiply(orderLine.getPrice()));
    List<OrderLine> items = orderLine.getItems();
    if (ObjectUtils.isEmpty(items)) {
      return;
    }
    for (OrderLine line : items) {
      updateValues(line, qtyCoef, priceCoef);
    }
  }

  protected void updateQty(OrderLine orderLine, BigDecimal qtyCoef) {
    BigDecimal qty = BigDecimal.valueOf(orderLine.getQuantity());
    BigDecimal newQty = qtyCoef.multiply(qty).setScale(0, RoundingMode.HALF_EVEN);
    int newQtyInt = newQty.intValue();
    orderLine.setQuantity(newQtyInt);
  }

  protected void updatePrice(OrderLine orderLine, BigDecimal priceCoef) {
    BigDecimal newPrice =
        priceCoef.multiply(orderLine.getPrice()).setScale(4, RoundingMode.HALF_EVEN);
    orderLine.setPrice(newPrice);
  }

  @SuppressWarnings("unchecked")
  protected OrderLine getDirtyLine(List<Map<String, Object>> list) {
    if (list == null) {
      return null;
    }
    for (Map<String, Object> orderLine : list) {
      Object items = orderLine.get("items");
      if (isChanged(orderLine)) {
        OrderLine bean = Mapper.toBean(OrderLine.class, orderLine);
        if (bean.getId() != null && bean.getItems() == null) {
          OrderLine line2 = Beans.get(OrderLineRepository.class).find(bean.getId());
          bean.setItems(line2.getItems());
        }
        return bean;
      }
      if (items == null) {
        continue;
      }
      OrderLine dirtyLine = getDirtyLine((List<Map<String, Object>>) items);
      if (dirtyLine != null) {
        return dirtyLine;
      }
    }
    return null;
  }

  protected boolean isChanged(Map<String, Object> isChanged) {
    return Boolean.TRUE.equals(isChanged.get("_changed"));
  }

  public Map<String, Object> toMapWithSubLine(OrderLine line) {
    List<OrderLine> subSoLineList = line.getItems();
    Map<String, Object> map = toMap(line);
    if (CollectionUtils.isEmpty(subSoLineList)) {
      return map;
    }
    List<Map<String, Object>> subSoLineMapList = new ArrayList<>();
    for (OrderLine subLine : subSoLineList) {
      subSoLineMapList.add(toMapWithSubLine(subLine));
    }
    map.put("items", subSoLineMapList);
    return map;
  }

  protected Map<String, Object> toMap(OrderLine line) {
    Map<String, Object> map = Mapper.toMap(line);
    map.put("oldQty", line.getQuantity());
    map.put("oldPrice", line.getPrice());
    return map;
  }

  protected boolean isEqual(OrderLine a, OrderLine b) {
    boolean isId = b.getId() != null && a.getId() != null;
    return isId
        ? a.getId().equals(b.getId())
        : (a.getCid() != null && a.getCid().equals(b.getCid()));
  }
}
