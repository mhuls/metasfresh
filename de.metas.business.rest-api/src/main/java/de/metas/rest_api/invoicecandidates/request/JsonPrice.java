package de.metas.rest_api.invoicecandidates.request;

import java.math.BigDecimal;

import io.swagger.annotations.ApiModelProperty;
import lombok.Value;

/*
 * #%L
 * de.metas.business.rest-api
 * %%
 * Copyright (C) 2019 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@Value
public class JsonPrice
{
	@ApiModelProperty(position = 10, required = false, //
			value = "Optional, to override the value as computed by metasfresh for the respective invoice candidate's property.\n"
					+ "To unset an existing candiate's override value, you can:\n"
					+ "- either use `SyncAdvice.IfExists.UPDATE_REMOVE` and set this property to `null`"
					+ "- or (preferred) use `\"unsetValue\" : true`")
	BigDecimal value;

	String currencyCode;

	@ApiModelProperty(position = 20, required = false, //
			value = "Identify which unit this about")
	String priceUomCode;
}
