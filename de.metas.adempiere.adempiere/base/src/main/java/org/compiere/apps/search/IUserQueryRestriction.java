package org.compiere.apps.search;

import org.compiere.model.MQuery;
import org.compiere.util.ValueNamePair;

import com.google.common.collect.ImmutableMap;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public interface IUserQueryRestriction
{
	public static IUserQueryRestriction newInstance()
	{
		return new UserQueryRestriction();
	}
	
	public static enum Join
	{
		AND("AND"), OR("OR");

		private static final ImmutableMap<String, Join> codeToJoin = ImmutableMap.<String, Join> builder()
				.put(AND.getCode(), AND)
				.put(OR.getCode(), OR)
				.build();

		private final String code;

		Join(final String code)
		{
			this.code = code;
		}

		public String getCode()
		{
			return code;
		}

		public static final Join forCodeOrAND(final String code)
		{
			final Join join = codeToJoin.get(code);
			return join != null ? join : AND;
		}
	}

	//@formatter:off
	void setJoin(Join join);
	Join getJoin();
	//@formatter:on

	//@formatter:off
	IUserQueryField getSearchField();
	void setSearchField(final IUserQueryField searchField);
	//@formatter:on

	//@formatter:off
	void setOperator(ValueNamePair operator);
	ValueNamePair getOperator();
	//@formatter:on

	default boolean isBinaryOperator()
	{
		final ValueNamePair operator = getOperator();
		return operator != null && operator.equals(MQuery.OPERATORS[MQuery.BETWEEN_INDEX]);
	}

	//@formatter:off
	Object getValue();
	void setValue(Object value);
	//@formatter:on

	//@formatter:off
	Object getValueTo();
	void setValueTo(Object valueTo);
	//@formatter:on
}
