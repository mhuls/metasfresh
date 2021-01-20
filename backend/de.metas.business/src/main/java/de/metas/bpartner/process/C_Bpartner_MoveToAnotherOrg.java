/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2021 metas GmbH
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

package de.metas.bpartner.process;

import de.metas.bpartner.BPartnerId;
import de.metas.bpartner.service.IBPartnerDAO;
import de.metas.organization.OrgId;
import de.metas.process.IProcessPrecondition;
import de.metas.process.IProcessPreconditionsContext;
import de.metas.process.JavaProcess;
import de.metas.process.Param;
import de.metas.process.ProcessPreconditionsResolution;
import de.metas.util.Services;
import lombok.NonNull;
import org.adempiere.model.InterfaceWrapperHelper;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_BPartner_Location;
import org.compiere.model.I_C_Location;

import java.util.List;

public class C_Bpartner_MoveToAnotherOrg extends JavaProcess implements IProcessPrecondition
{
	private static final String PARAM_Org_ID = "AD_Org_ID";
	@Param(parameterName = PARAM_Org_ID, mandatory = true)
	private OrgId orgId;

	@Override
	public ProcessPreconditionsResolution checkPreconditionsApplicable(@NonNull final IProcessPreconditionsContext context)
	{
		final BPartnerId bpartnerId = BPartnerId.ofRepoIdOrNull(context.getSingleSelectedRecordId());
		if (bpartnerId == null)
		{
			return ProcessPreconditionsResolution.rejectWithInternalReason("no BPartnerId");
		}

		return ProcessPreconditionsResolution.accept();
	}

	@Override
	protected String doIt() throws Exception
	{
		final BPartnerId bpartnerId = getBPartnerId();

		final I_C_BPartner newPartner = InterfaceWrapperHelper.copy()
				.setFrom(Services.get(IBPartnerDAO.class).getByIdInTrx(bpartnerId))
				.setSkipCalculatedColumns(true)
				.addTargetColumnNameToSkip(I_C_BPartner.COLUMNNAME_CreditorId)
				.addTargetColumnNameToSkip(I_C_BPartner.COLUMNNAME_DebtorId)
				.copyToNew(I_C_BPartner.class);

		newPartner.setAD_Org_ID(orgId.getRepoId());

		InterfaceWrapperHelper.saveRecord(newPartner);

		final List<I_C_BPartner_Location> bPartnerLocations = Services.get(IBPartnerDAO.class).retrieveBPartnerLocations(bpartnerId);

		for (final I_C_BPartner_Location bpLoc : bPartnerLocations)
		{
			final I_C_Location newLocation = InterfaceWrapperHelper.copy()
					.setFrom(bpLoc.getC_Location())
					.copyToNew(I_C_Location.class);

			newLocation.setAD_Org_ID(orgId.getRepoId());

			InterfaceWrapperHelper.saveRecord(newLocation);

			final I_C_BPartner_Location newBPartnerLocation = InterfaceWrapperHelper.copy()
					.setFrom(bpLoc)
					.copyToNew(I_C_BPartner_Location.class);

			newBPartnerLocation.setAD_Org_ID(orgId.getRepoId());

			newBPartnerLocation.setC_BPartner_ID(newPartner.getC_BPartner_ID());
			InterfaceWrapperHelper.saveRecord(newBPartnerLocation);
		}

		return MSG_OK;
	}

	private BPartnerId getBPartnerId()
	{
		return BPartnerId.ofRepoId(getRecord_ID());
	}

}
