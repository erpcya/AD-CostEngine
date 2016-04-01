/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.adempiere.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.adempiere.engine.AverageInvoiceCostingMethod;
import org.adempiere.engine.CostEngineFactory;
import org.adempiere.engine.CostingMethodFactory;
import org.adempiere.engine.StandardCostingMethod;
import org.compiere.acct.Doc;
import org.compiere.acct.DocLine_CostCollector;
import org.compiere.acct.Fact;
import org.compiere.acct.FactLine;
import org.compiere.model.I_M_Transaction;
import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MClient;
import org.compiere.model.MCostDetail;
import org.compiere.model.MCostElement;
import org.compiere.model.MCostType;
import org.compiere.model.MDocType;
import org.compiere.model.MFactAcct;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInventory;
import org.compiere.model.MInventoryLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MLandedCostAllocation;
import org.compiere.model.MMatchInv;
import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;
import org.compiere.model.MOrder;
import org.compiere.model.MProduction;
import org.compiere.model.MProductionLine;
import org.compiere.model.MProjectIssue;
import org.compiere.model.MRole;
import org.compiere.model.MTransaction;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.ProductCost;
import org.compiere.model.Query;
import org.compiere.model.X_M_CostType;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.eevolution.model.I_PP_Cost_Collector;
import org.eevolution.model.I_PP_Order;
import org.eevolution.model.MPPCostCollector;
import org.eevolution.model.MPPOrder;
import org.eevolution.model.X_PP_Cost_Collector;


/**
 *	Validator Example Implementation
 *	x
 *	@author Jorg Janke
 *	@version $Id: MyValidator.java,v 1.2 2006/07/30 00:51:57 jjanke Exp $
 */
public class CEValidator implements ModelValidator
{
	/**
	 *	Constructor.
	 */
	public CEValidator ()
	{
		super ();
	}	//	MyValidator
	
	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(CEValidator.class);
	/** Client			*/
	private int		m_AD_Client_ID = -1;
	/** User	*/
	private int		m_AD_User_ID = -1;
	/** Role	*/
	private int		m_AD_Role_ID = -1;
	
	/**
	 *	Initialize Validation
	 *	@param engine validation engine 
	 *	@param client client
	 */
	public void initialize (ModelValidationEngine engine, MClient client)
	{
		//client = null for global validator
		if (client != null) {	
			m_AD_Client_ID = client.getAD_Client_ID();
			log.info(client.toString());
		}
		else  {
			log.info("Initializing global validator: "+this.toString());
		}
		
		//	We want to be informed when C_Order is created/changed
		//	We want to validate Order before preparing 
		engine.addDocValidate(MOrder.Table_Name, this);
		engine.addDocValidate(MInvoice.Table_Name, this);
		engine.addDocValidate(MInOut.Table_Name, this);
		engine.addDocValidate(MMovement.Table_Name, this);
		engine.addDocValidate(MProduction.Table_Name, this);
		engine.addDocValidate(MInventory.Table_Name, this);
		engine.addDocValidate(MProjectIssue.Table_Name, this);
		engine.addDocValidate(MPPCostCollector.Table_Name, this);
		engine.addDocValidate(MMatchInv.Table_Name, this);
		engine.addDocValidate(MAllocationHdr.Table_Name, this);
		engine.addDocValidate(MPPOrder.Table_Name, this);		
		


	}	//	initialize

    /**
     *	Model Change of a monitored Table.
     *	Called after PO.beforeSave/PO.beforeDelete
     *	when you called addModelChange for the table
     *	@param po persistent object
     *	@param type TYPE_
     *	@return error message or null
     *	@exception Exception if the recipient wishes the change to be not accept.
     */
	public String modelChange (PO po, int type) throws Exception
	{	
		if (type == TYPE_AFTER_NEW && po.get_TableName().equals(MFactAcct.Table_Name))
		{
			MFactAcct fa = (MFactAcct)po;
			if (fa.getAD_Table_ID()!=735)
				return "";
			MAllocationLine alo = new MAllocationLine(po.getCtx(), fa.getLine_ID(), po.get_TrxName());
			if (alo.getC_Payment_ID() != 0)
				fa.setGL_Category_ID(alo.getC_Payment().getC_DocType().getGL_Category_ID());
			fa.saveEx();
		}
		return "";
	}	//	modelChange
	
	/**
	 *	Validate Document.
	 *	Called as first step of DocAction.prepareIt 
     *	when you called addDocValidate for the table.
     *	Note that totals, etc. may not be correct.
	 *	@param po persistent object
	 *	@param timing see TIMING_ constants
     *	@return error message or null
	 */
	public String docValidate (PO po, int timing)
	{
		String error = null;
		log.info(po.get_TableName() + " Timing: "+timing);

		if (timing == TIMING_AFTER_COMPLETE){/*	
			
			if (po.get_TableName().equals(MInOut.Table_Name))
			{
				error = calculateMInout((MInOut)po);
			}
			else if (po.get_TableName().equals(MOrder.Table_Name))
			{
				//error = testValidator((MOrder)po);
			}

			else if (po.get_TableName().equals(MInventory.Table_Name))
			{
				error = calculateMInventory((MInventory)po);
			}

			else if (po.get_TableName().equals(MMovement.Table_Name))
			{
				error = calculateMMovement((MMovement)po);
			}
			else if (po.get_TableName().equals(MProduction.Table_Name))
			{
				MProduction prod = new MProduction(po.getCtx(), po.get_ID(), po.get_TrxName());
				error = calculateMProduction(prod);
			}
			else if (po.get_TableName().equals(MProjectIssue.Table_Name))
			{
				error = calculateMProjectIssue((MProjectIssue)po);
			}
			else if (po.get_TableName().equals(MPPCostCollector.Table_Name))
			{
				error = calculateCostCollector((MPPCostCollector)po);
			}
			else if (po.get_TableName().equals(MInvoice.Table_Name))
			{
				error = calculateLandedCost((MInvoice)po);
			}
			else if (po.get_TableName().equals(MMatchInv.Table_Name))
			{
				error = calculateMMatchInv((MMatchInv)po);
			}
		*/}
if (timing == TIMING_PREPAREPOST) 
{	
			
			if (po.get_TableName().equals(MInOut.Table_Name))
			{
				error = calculateMInout((MInOut)po);
			}
			else if (po.get_TableName().equals(MOrder.Table_Name))
			{
				//error = testValidator((MOrder)po);
			}

			else if (po.get_TableName().equals(MInventory.Table_Name))
			{
				error = calculateMInventory((MInventory)po);
			}

			else if (po.get_TableName().equals(MMovement.Table_Name))
			{
				error = calculateMMovement((MMovement)po);
			}
			else if (po.get_TableName().equals(MProduction.Table_Name))
			{
				error = calculateMProduction((MProduction)po);
			}
			else if (po.get_TableName().equals(MProjectIssue.Table_Name))
			{
				error = calculateMProjectIssue((MProjectIssue)po);
			}
			else if (po.get_TableName().equals(MPPCostCollector.Table_Name))
			{
				error = calculateCostCollector((MPPCostCollector)po);
			}
			else if (po.get_TableName().equals(MInvoice.Table_Name))
			{
				error = calculateLandedCost((MInvoice)po);
			}
			else if (po.get_TableName().equals(MMatchInv.Table_Name))
			{
				error = calculateMMatchInv((MMatchInv)po);
			}
		}
		if (timing == TIMING_BEFORE_POST)
		{/*
			if (po.get_TableName().equals(MPPCostCollector.Table_Name))					
				error = balanceWIP(po);*/
			if (po.get_TableName().equals(MAllocationHdr.Table_Name))
				error = updateAllocationCategory(po);
			
		}
		
		if (timing == TIMING_AFTER_CLOSE && po.get_TableName().equals(I_PP_Order.Table_Name))
			createCC_UpdateAverage(po);
				
		
		return error;
	}	//	docValidate	
	
	
	
	/**
	 *	User Login.
	 *	Called when preferences are set
	 *	@param AD_Org_ID org
	 *	@param AD_Role_ID role
	 *	@param AD_User_ID user
	 *	@return error message or null
	 */
	public String login (int AD_Org_ID, int AD_Role_ID, int AD_User_ID)
	{
		log.info("AD_User_ID=" + AD_User_ID);
		m_AD_User_ID = AD_User_ID;
		m_AD_Role_ID = AD_Role_ID;
		return null;
	}	//	login

	/**
	 *	Get Client to be monitored
	 *	@return AD_Client_ID client
	 */
	public int getAD_Client_ID()
	{
		return m_AD_Client_ID;
	}	//	getAD_Client_ID

	
	/**
	 * 	String Representation
	 *	@return info
	 */
	public String toString ()
	{
		StringBuffer sb = new StringBuffer ("CEValidator");
		return sb.toString ();
	}	//	toString

	/**
	 * Sample Validator Before Save Properties - to set mandatory properties on users
	 * avoid users changing propertiesconfirm
	 */
	public void beforeSaveProperties() {
		// not for SuperUser or role SysAdmin
		if (   m_AD_User_ID == 0  // System
			|| m_AD_User_ID == 100   // SuperUser
			|| m_AD_Role_ID == 0  // System Administrator
			|| m_AD_Role_ID == 1000000)  // ECO Admin
			return;

		log.info("Setting default Properties");

		MRole role = MRole.get(Env.getCtx(), m_AD_Role_ID);

		// Example - if you don't want user to select auto commit property
		// Ini.setProperty(Ini.P_A_COMMIT, false);
		
		// Example - if you don't want user to select auto login
		// Ini.setProperty(Ini.P_A_LOGIN, false);

		// Example - if you don't want user to select store password
		// Ini.setProperty(Ini.P_STORE_PWD, false);

		// Example - if you want your user inherit ALWAYS the show accounting from role
		// Ini.setProperty(Ini.P_SHOW_ACCT, role.isShowAcct());
		
		// Example - if you want to avoid your user from changing the working date
		/*
		Timestamp DEFAULT_TODAY =	new Timestamp(System.currentTimeMillis());
		//  Date (remove seconds)
		DEFAULT_TODAY.setHours(0);
		DEFAULT_TODAY.setMinutes(0);
		DEFAULT_TODAY.setSeconds(0);
		DEFAULT_TODAY.setNanos(0);
		Ini.setProperty(Ini.P_TODAY, DEFAULT_TODAY.toString());
		Env.setContext(Env.getCtx(), "#Date", DEFAULT_TODAY);
		*/
		

	}	// beforeSaveProperties
	
	
	private String calculateMInout(MInOut inout)
	{
		for (MInOutLine sLine:inout.getLines())
		{

			for (MTransaction mtrx:trxs_getByDocumentLine(sLine, MInOutLine.Table_Name))
			{
				if (mtrx== null)
					continue;
				CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx, mtrx.getDocumentLine());
			}			
		}
		return "";
	}

	private String calculateMInventory(MInventory inv)
	{
		for (MInventoryLine sLine:inv.getLines(true))
		{
			for (MTransaction mtrx:trxs_getByDocumentLine(sLine, MInventoryLine.Table_Name))
			{
				CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx,mtrx.getDocumentLine());
			}			
		}
		return "";
	}
	

	private String calculateMProduction(MProduction prod)
	{
		for (MProductionLine pLine:prod.getLines())
		{
			for (MTransaction mtrx:trxs_getByDocumentLine(pLine, MProductionLine.Table_Name))
			{
				CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx,mtrx.getDocumentLine());
			}			
		}
		return "";
	}

	private String calculateMProjectIssue(MProjectIssue issue)
	{
		for (MTransaction mtrx:trxs_getByDocumentLine(issue, MProjectIssue.Table_Name))
		{
			CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx, mtrx.getDocumentLine());
		}	
		return "";
	}
	



	

	private String calculateCostCollector(PO po)
	{
		/*if (cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt) 
				&& !cc.getPP_Order().getDocStatus().equals(MPPOrder.DOCSTATUS_Closed))
			return "";*/
		MPPCostCollector cc = (MPPCostCollector)po;
		if (cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt)
				|| cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_ComponentIssue))
		for (MTransaction mtrx:trxs_getByDocumentLine(cc, MPPCostCollector.Table_Name))
		{
			CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx,mtrx.getDocumentLine());
		}
		if (cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_UpdateAverage))
			balanceWIP(po);
		
		else if (cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl))
		{
			final StandardCostingMethod standardCM = (StandardCostingMethod) CostingMethodFactory.get()
					.getCostingMethod(X_M_CostType.COSTINGMETHOD_StandardCosting);
			standardCM.createActivityControl(cc);
			
		}
		/*
		else if (cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_MethodChangeVariance)
				|| cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_RateVariance)
				|| cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_UsegeVariance))
			CostEngineFactory.getCostEngine(getAD_Client_ID()).createUsageVariances(cc);
		else if (cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl))
			CostEngineFactory.getCostEngine(getAD_Client_ID()).createActivityControl(cc);*/
		return "";
	}

	private String calculateMMovement(MMovement movement)
	{
		for (MMovementLine sLine:movement.getLines(true))
		{
			for (MTransaction mtrx:trxs_getByDocumentLine(sLine, MMovementLine.Table_Name))
			{
				CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx,mtrx.getDocumentLine());
			}			
		}
		Trx trx = Trx.get(movement.getDocumentNo(), true);
		trx.commit();
		trx.close();
		return "";
	}
	
	
	private String calculateLandedCost(MInvoice inv)
	{
		if (inv.get_TrxName()== null || inv.isSOTrx())
			return "";
		if (inv.isSOTrx())
			return "";
		for (MInvoiceLine iLine:inv.getLines())
		{
			MLandedCostAllocation[] lcas = MLandedCostAllocation.getOfInvoiceLine(
					inv.getCtx(), iLine.getC_InvoiceLine_ID(), inv.get_TrxName());
			for (MLandedCostAllocation allocation:lcas)
			{
				MInOutLine ioLine = (MInOutLine) allocation.getM_InOutLine();
				for (MTransaction mtrx: MTransaction.getByInOutLine(ioLine))
				{
						CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx, allocation);
				}		
			}
		}
		return "";
	}

	private String calculateMMatchInv(MMatchInv m_inv)
	{	
		MInOutLine inout_line = (MInOutLine) m_inv.getM_InOutLine();
		for (MTransaction mtrx: MTransaction.getByInOutLine(inout_line))
			{
				CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetail(mtrx, m_inv);
			}
		return "";
	}
	

	public  MTransaction[] trxs_getByDocumentLine(PO po, String tablename)
	{
		final String column_id = tablename + "_ID";	
		MTransaction[]	mtrxs = null;
		if (po instanceof MPPCostCollector)
		{
			MPPCostCollector cc = (MPPCostCollector)po;
			if ((cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt)
					|| cc.getCostCollectorType().equals(MPPCostCollector.COSTCOLLECTORTYPE_ComponentIssue)))
			{
				final String whereClause = column_id + "=?";
				List<MTransaction> list = new Query (po.getCtx(), I_M_Transaction.Table_Name, whereClause, po.get_TrxName())
				.setClient_ID()
				.setParameters(po.get_ID())
				.list();
				mtrxs = list.toArray(new MTransaction[list.size()]);
			}
			else
			{
				String whereClause = " exists (select 1 from pp_cost_collector pc" +
						" where pc.pp_cost_collector_ID=m_transaction.pp_Cost_collector_ID and costcollectortype =? " +
						" and pc.pp_order_ID=?)";
				List<MTransaction> cclist = new Query(po.getCtx(), MTransaction.Table_Name, whereClause, po.get_TrxName())
				.setParameters(MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt,cc.getPP_Order_ID())
				.list();
				mtrxs = cclist.toArray(new MTransaction[cclist.size()]);
			}
		}
		else
		{
			final String whereClause = column_id + "=?";
			List<MTransaction> list = new Query (po.getCtx(), I_M_Transaction.Table_Name, whereClause, po.get_TrxName())
			.setClient_ID()
			.setParameters(po.get_ID())
			.setOrderBy("M_Transaction_ID")
			.list();
			mtrxs = list.toArray(new MTransaction[list.size()]);
		}
		return mtrxs;
	}
	
	private String balanceWIP(PO po)
	{	
		MPPCostCollector cc = (MPPCostCollector)po;
		MPPCostCollector cc_mat = new Query(cc.getCtx(), MPPCostCollector.Table_Name, "pp_order_ID=? and COSTCOLLECTORTYPE=?", cc.get_TrxName())
		.setParameters(cc.getPP_Order_ID(), MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt)
		.setOrderBy(MPPCostCollector.COLUMNNAME_PP_Cost_Collector_ID + " desc")
		.first();

		Doc doc = cc_mat.getDoc();
		DocLine_CostCollector docLine = new DocLine_CostCollector(po, doc);
		MAcctSchema[] ass = MAcctSchema.getClientAcctSchema(po.getCtx(),	po.getAD_Client_ID());	
		MCostElement ce = MCostElement.getByMaterialCostElementType(po);

		for (MAcctSchema as:ass)
		{
			if (!as.getM_CostType().getCostingMethod().equals(MCostType.COSTINGMETHOD_AverageInvoice))
				continue;
			MCostType ct = (MCostType)as.getM_CostType();


			MAccount wip = docLine.getAccount(ProductCost.ACCTTYPE_P_WorkInProcess, as);
			String whereClause = " ad_table_ID = 53035 " +
					"and userelement1_id=?  and account_ID=? and postingtype='A'";
			BigDecimal diff = new Query(cc.getCtx(), MFactAcct.Table_Name, whereClause, cc.get_TrxName())
				.setParameters(cc.getPP_Order_ID(), wip.getAccount_ID())
				.aggregate("(amtacctdr - amtacctcr)", Query.AGGREGATE_SUM);
			if (diff != null && diff.compareTo(Env.ZERO)!= 0)
			{
				if (!ce.getCostElementType().equals(MCostElement.COSTELEMENTTYPE_Material))
					continue;

				whereClause = "pp_cost_collector_ID =? and m_costtype_ID=? and m_costelement_ID=?";
				MCostDetail cd = new Query(cc.getCtx(), MCostDetail.Table_Name, whereClause, cc.get_TrxName())
					.setParameters(cc.getPP_Cost_Collector_ID(), ct.getM_CostType_ID(), ce.getM_CostElement_ID())
					.first();
				if (cd!=null)
					return "";
				final AverageInvoiceCostingMethod AverageCostingMethod = (AverageInvoiceCostingMethod) CostingMethodFactory.get()
						.getCostingMethod(X_M_CostType.COSTINGMETHOD_AverageInvoice);
				AverageCostingMethod.createUpdateAverageCostDetail(
						cc, diff, Env.ZERO, cc_mat.getM_Product(), as, ct, ce);
			}
		}
		return "";
	}
	

	private String updateAllocationCategory(PO po)
	{	
		MAllocationHdr ah = (MAllocationHdr)po;
		Doc doc = ah.getDoc();
		
		ArrayList<Fact> facts = doc.getFacts();
		// one fact per acctschema
		for (int i = 0; i < facts.size(); i++)
		{
			Fact fact = facts.get(i);
			MAcctSchema as = fact.getAcctSchema();
			for (FactLine fline:fact.getLines())
			{
				MAllocationLine alo = new MAllocationLine(fline.getCtx(), fline.getLine_ID(), fline.get_TrxName());
				if (alo.getC_Payment() != null)
				{
					fline.setGL_Category_ID(alo.getC_Payment().getC_DocType().getGL_Category_ID());
					fline.saveEx();
				}
				else if (alo.getC_CashLine() != null)
				{
					int gl_category_ID = new Query(fline.getCtx(), MDocType.Table_Name, "AD_Client_ID=? AND DocBaseType=?", fline.get_TrxName())
						.setParameters(ah.getAD_Client_ID(), MDocType.DOCBASETYPE_CashJournal)
						.setOnlyActiveRecords(true)
						.aggregate(MDocType.COLUMNNAME_GL_Category_ID, Query.AGGREGATE_MIN).intValue();
					
					fline.setGL_Category_ID(gl_category_ID);
					fline.saveEx();
				}
			}
		}

		return  "";
	}
	
	

	private String createCC_UpdateAverage(PO po)
	{	
		MPPOrder order = (MPPOrder)po;
		StringBuffer  whereClause = new StringBuffer();
		whereClause.append(I_PP_Cost_Collector.COLUMNNAME_CostCollectorType + " = '" + X_PP_Cost_Collector.COSTCOLLECTORTYPE_UpdateAverage)
		.append("' and pp_order_ID =?");
		for (MAcctSchema as:MAcctSchema.getClientAcctSchema(po.getCtx(),po.getAD_Client_ID()))
		{
			if (as.getM_CostType().getCostingMethod().equals(MCostType.COSTINGMETHOD_AverageInvoice))
			{
				int id = new Query(po.getCtx(), I_PP_Cost_Collector.Table_Name, whereClause.toString(), po.get_TrxName())
				.setParameters(po.get_ID())
				.firstId();
				if (id == -1)
				{
					MPPCostCollector cc = new MPPCostCollector(order);
					cc.setCostCollectorType(X_PP_Cost_Collector.COSTCOLLECTORTYPE_UpdateAverage);
					cc.setC_DocTypeTarget_ID(MDocType.DOCBASETYPE_ManufacturingCostCollector);
					MPPCostCollector cc_mat = new Query(cc.getCtx(), MPPCostCollector.Table_Name, "pp_order_ID=? and COSTCOLLECTORTYPE=?", cc.get_TrxName())
					.setParameters(cc.getPP_Order_ID(), MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt)
					.setOrderBy(MPPCostCollector.COLUMNNAME_PP_Cost_Collector_ID + " desc")
					.first();
					cc.setM_AttributeSetInstance_ID(cc_mat.getM_AttributeSetInstance_ID());
					cc.setMovementQty(cc_mat.getMovementQty());
					cc.setC_DocType_ID(cc.getC_DocTypeTarget_ID());
					cc.setProcessed(true);
					cc.setDocStatus(MPPCostCollector.DOCSTATUS_Completed);
					cc.setDocAction(MPPCostCollector.DOCACTION_Close);
					cc.setDateAcct(order.getUpdated());
					cc.saveEx();
					//cc.completeIt();
					break;
				}
			}
		} 
		return "";
	}
	
	
	
	
	
	

	

	
	
	
	

}	//	MyValidator
