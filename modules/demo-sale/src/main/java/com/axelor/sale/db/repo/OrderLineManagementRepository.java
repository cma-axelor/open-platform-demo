/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2024 Axelor (<http://axelor.com>).
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
package com.axelor.sale.db.repo;

import com.axelor.sale.db.Order;
import com.axelor.sale.db.OrderLine;
import java.util.Map;

public class OrderLineManagementRepository extends OrderLineRepository {

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {

    if (context.get("_model") != null
        && (context.get("_model").equals(Order.class.getName())
            || context.get("_model").equals(OrderLine.class.getName()))
        && (context.get("id") != null || context.get("_field_ids") != null)) {
      Long id = (Long) json.get("id");
      if (id != null) {
        OrderLine saleOrderLine = find(id);
        json.put("oldQty", saleOrderLine.getQuantity());
        json.put("oldPrice", saleOrderLine.getPrice());
      }
    }
    return super.populate(json, context);
  }
}
