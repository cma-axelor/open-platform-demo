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
package com.axelor.sale.service;

import com.axelor.common.ObjectUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.inject.Beans;
import com.axelor.sale.db.Order;
import com.axelor.sale.db.OrderLine;
import com.axelor.sale.db.Tax;
import com.axelor.sale.db.repo.OrderLineRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import javax.validation.ValidationException;

public class SaleOrderService {

  public void validate(Order order) {
    if (order != null
        && order.getConfirmDate() != null
        && order.getConfirmDate().isBefore(order.getOrderDate())) {
      throw new ValidationException("Invalid sale order, confirm date is before order date.");
    }
  }

  public Order calculate(Order order) {

    BigDecimal amount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;

    if (!ObjectUtils.isEmpty(order.getItems())) {
      for (OrderLine item : order.getItems()) {
        BigDecimal value = item.getPrice().multiply(new BigDecimal(item.getQuantity()));
        BigDecimal taxValue = BigDecimal.ZERO;

        if (!ObjectUtils.isEmpty(item.getTaxes())) {
          for (Tax tax : item.getTaxes()) {
            taxValue = taxValue.add(tax.getRate().multiply(value));
          }
        }

        amount = amount.add(value);
        taxAmount = taxAmount.add(taxValue);
      }
    }

    order.setAmount(amount.setScale(4, RoundingMode.HALF_UP));
    order.setTaxAmount(taxAmount.setScale(4, RoundingMode.HALF_UP));
    order.setTotalAmount(amount.add(taxAmount).setScale(4, RoundingMode.HALF_UP));

    return order;
  }

  public List<OrderLine> updateRelatedLines(OrderLine dirtyLine, Order order) {
    List<OrderLine> items = order.getItems();
    if (items == null || (dirtyLine.getOldQty() == 0 || dirtyLine.getOldPrice().signum() == 0)) {
      return items;
    }
    updateChild(dirtyLine);
    replaceDirtyLineInItems(dirtyLine, items);
    updateParent(dirtyLine, items);
    return items;
  }

  protected void updateChild(OrderLine line) {
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

  protected void updateValues(OrderLine orderLine, BigDecimal qtyCoef, BigDecimal priceCoef) {
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
    BigDecimal qty = BigDecimal.valueOf(orderLine.getQuantity() == 0 ? 1 : orderLine.getQuantity());
    BigDecimal newQty = qtyCoef.multiply(qty).setScale(0, RoundingMode.HALF_EVEN);
    int newQtyInt = newQty.intValue();
    orderLine.setQuantity(newQtyInt);
  }

  protected void updatePrice(OrderLine orderLine, BigDecimal priceCoef) {
    BigDecimal newPrice =
        priceCoef
            .multiply(orderLine.getPrice().signum() == 0 ? BigDecimal.ONE : orderLine.getPrice())
            .setScale(4, RoundingMode.HALF_EVEN);
    orderLine.setPrice(newPrice);
  }

  protected boolean replaceDirtyLineInItems(OrderLine dirtyLine, List<OrderLine> items) {
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

  protected void updateParent(OrderLine dirtyLine, List<OrderLine> list) {
    for (OrderLine orderLine : list) {
      if (isOrHasDirtyLine(orderLine, dirtyLine)) {
        compute(orderLine);
      }
    }
  }

  protected void compute(OrderLine orderLine) {
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

  protected boolean isOrHasDirtyLine(OrderLine orderLine, OrderLine dirtyLine) {

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

  protected BigDecimal getPriceCoef(OrderLine line) {
    BigDecimal oldPrice = line.getOldPrice().signum() == 0 ? BigDecimal.ONE : line.getOldPrice();
    return line.getPrice().divide(oldPrice, 4, RoundingMode.HALF_EVEN);
  }

  protected BigDecimal getQtyCoef(OrderLine line) {
    Integer oldQty = line.getOldQty() == 0 ? 1 : line.getOldQty();
    return BigDecimal.valueOf(line.getQuantity())
        .divide(BigDecimal.valueOf(oldQty), 4, RoundingMode.HALF_EVEN);
  }

  @SuppressWarnings("unchecked")
  public OrderLine findDirtyLine(List<Map<String, Object>> list) {
    if (list == null) {
      return null;
    }
    for (Map<String, Object> orderLine : list) {
      Object items = orderLine.get("items");
      if (isChanged(orderLine)) {
        return getDirtyLine(orderLine);
      }
      if (items == null) {
        continue;
      }
      OrderLine dirtyLine = findDirtyLine((List<Map<String, Object>>) items);
      if (dirtyLine != null) {
        return dirtyLine;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  protected OrderLine getDirtyLine(Map<String, Object> orderLine) {
    OrderLine bean = Mapper.toBean(OrderLine.class, orderLine);
    if (orderLine.get("_original") != null) {
      OrderLine oldValue =
          Mapper.toBean(OrderLine.class, (Map<String, Object>) orderLine.get("_original"));
      bean.setOldQty(oldValue.getQuantity());
      bean.setOldPrice(oldValue.getPrice());
    }
    if (bean.getId() != null && bean.getItems() == null) {
      OrderLine line2 = Beans.get(OrderLineRepository.class).find(bean.getId());
      bean.setItems(line2.getItems());
    }
    return bean;
  }

  protected boolean isChanged(Map<String, Object> isChanged) {
    return Boolean.TRUE.equals(isChanged.get("_changed"));
  }

  protected boolean isEqual(OrderLine a, OrderLine b) {
    boolean isId = b.getId() != null && a.getId() != null;
    return isId
        ? a.getId().equals(b.getId())
        : (a.getCid() != null && a.getCid().equals(b.getCid()));
  }
}
