DROP FUNCTION if exists getCurrentCost(numeric, date, numeric, numeric, numeric);
CREATE OR REPLACE FUNCTION getCurrentCost(p_M_Product_ID numeric, p_Date date, p_AcctSchema_ID numeric,
                                          p_M_CostElement_Id numeric,
                                          p_AD_Client_ID numeric, p_AD_Org_ID numeric)
    returns numeric
AS
$BODY$
WITH x as
         (
             SELECT m_costdetail_ID,
                    cd.prev_currentcostprice,
                    cd.M_Product_ID,
                    COALESCE(mi.dateAcct, mpo.dateAcct, pp.dateAcct, inv.movementDate, m.MovementDate, io.dateAcct,
                             NULL) as dateAcct

             from m_costdetail cd

                      JOIN AD_Org org on cd.ad_org_ID = org.ad_org_id


                      left join M_MatchInv mi ON mi.m_matchinv_id = cd.m_matchinv_id
                      left JOIN M_MatchPO mpo on mpo.M_MatchPO_ID = cd.m_MatchPO_ID
                      left JOIN PP_Cost_Collector pp ON pp.PP_Cost_Collector_ID = cd.pp_cost_collector_id
                      left JOIN m_inventoryline il on il.m_inventoryline_id = cd.m_inventoryline_id
                      LEFT JOIN M_Inventory inv on inv.m_inventory_id = il.m_inventory_id
                      LEFT JOIN M_MovementLine ml on ml.m_movementline_id = cd.m_movementline_id
                      LEFT JOIN M_Movement m on m.m_movement_id = ml.m_movement_ID
                      LEFT JOIN m_inoutline iol on iol.m_inoutline_id = cd.m_inoutline_id
                      LEFT JOIN M_InOut io on iol.M_InOut_ID = io.M_InOut_ID

             WHERE cd.ischangingcosts = 'Y'
               and cd.c_acctschema_id = p_acctSchema_ID
               and cd.m_costelement_id = p_M_CostElement_Id
               and cd.M_Product_ID = p_M_Product_ID
               and cd.ad_client_id = p_AD_Client_ID
               and cd.ad_org_ID = p_AD_Org_ID
         )


Select COALESCE((SELECT prev_currentcostprice from x where dateAcct > p_Date limit 1), cost.currentCostPrice)
           as currentCostPrice

from M_Cost cost


WHERE cost.m_product_id = p_M_Product_ID
  and cost.c_acctschema_id = p_acctSchema_ID
  and cost.m_costelement_id = p_M_CostElement_Id
  and cost.ad_client_id = p_AD_Client_ID
  and cost.AD_Org_Id = p_AD_Org_ID;


$BODY$
    LANGUAGE SQL
    STABLE
;







DROP FUNCTION IF EXISTS report.M_Cost_CostPrice_Function(IN p_keydate timestamp with time zone,
    IN p_M_Product_ID numeric(10, 0),
    IN p_M_Warehouse_ID numeric(10, 0),
    IN p_showDetails character varying,
    IN p_ad_language character varying(6),
    IN p_AcctSchema_ID numeric(10, 0),
    IN p_M_CostElement_ID numeric(10, 0),
    IN p_AD_Client_ID numeric(10, 0),
    IN p_AD_Org_ID numeric(10, 0));


DROP FUNCTION IF EXISTS report.M_Cost_CostPrice_Function(IN p_keydate timestamp with time zone,
    IN p_M_Product_ID numeric(10, 0),
    IN p_M_Warehouse_ID numeric(10, 0),
    IN p_showDetails character varying,
    IN p_ad_language character varying(6),
    IN p_AD_Client_ID numeric(10, 0),
    IN p_AD_Org_ID numeric(10, 0));

CREATE OR REPLACE FUNCTION report.M_Cost_CostPrice_Function(IN p_keydate timestamp with time zone,
                                                            IN p_M_Product_ID numeric(10, 0),
                                                            IN p_M_Warehouse_ID numeric(10, 0),
                                                            IN p_showDetails character varying,
                                                            IN p_ad_language character varying(6),
                                                            IN p_AD_Client_ID numeric(10, 0),
                                                            IN p_AD_Org_ID numeric(10, 0))


    RETURNS TABLE
            (
                combination   character varying,
                description   character varying,
                activity      character varying,
                WarehouseName character varying,
                ProductValue  character varying,
                ProductName   character varying,
                qty           numeric,
                uomsymbol     character varying,
                costprice     numeric,
                TotalAmt      numeric
            )


AS
$$


SELECT vc.combination,
       vc.description,
       a.name                                  AS Activity,
       wh.name                                 AS WarehouseName,
       p.value                                 AS ProductValue,
       COALESCE(pt.Name, p.Name)               AS ProductName,
       qty,
       COALESCE(uomt.UOMSymbol, uom.UOMSymbol) AS UOMSymbol,
       CostPrice,
       qty * CostPrice                         AS TotalAmt
FROM (
         SELECT pa.P_Asset_acct                                                          AS C_ValidCombination_ID,
                wh.C_Activity_ID,
                wh.M_Warehouse_ID,
                hus.M_Product_ID,
                hus.C_UOM_ID,
                SUM(uomconvert(hus.m_product_id, hutl.c_uom_id, hus.c_uom_id, hutl.qty)) as qty,
                COALESCE(
                        getCurrentCost(hus.M_Product_ID,
                                       p_keydate::date,
                                       schema.c_acctschema_id,
                                       costElement.m_costelement_id,
                                       p_AD_Client_ID,
                                       p_AD_org_ID)
                    , 0::numeric)                                                        AS CostPrice
         FROM M_Warehouse wh
                  JOIN ad_org org on wh.ad_org_id = org.ad_org_ID
                  JOIN AD_Client client on wh.ad_client_ID = client.ad_client_id
                  JOIN ad_clientinfo clientInfo on client.ad_client_id = clientInfo.ad_client_id
                  JOIN c_acctschema schema on clientInfo.c_acctschema1_id = schema.c_acctschema_id
                  JOIN m_costelement costElement
                       on schema.costingmethod = costElement.costingmethod and costElement.isactive = 'Y' /* There should only be one active M_CostElement
              entry for a certain CostingMethod because of the unique index M_CostElement_CostingMethod_Unique. Please, keep this function in sync with this index */


                  LEFT OUTER JOIN M_Locator l
                                  ON wh.M_Warehouse_ID = l.M_Warehouse_ID AND l.isActive = 'Y'
                  LEFT OUTER JOIN M_HU_Trx_line hutl ON l.M_Locator_ID = hutl.M_locator_ID AND hutl.isActive = 'Y'
                  LEFT OUTER JOIN M_HU_Item item ON hutl.VHU_Item_ID = item.M_HU_Item_ID AND item.isActive = 'Y'
                  LEFT OUTER JOIN M_HU hu ON item.M_HU_ID = hu.M_HU_ID AND hu.isActive = 'Y'
                  LEFT OUTER JOIN M_HU_Storage hus ON hu.M_HU_ID = hus.M_HU_ID AND hus.isActive = 'Y'
                  LEFT OUTER JOIN M_Product_acct pa ON hus.M_Product_ID = pa.M_Product_ID AND pa.isActive = 'Y'
         WHERE hutl.DateTrx::date <= $1
           AND hutl.huStatus IN ('A', 'S') -- qonly display transactions if status is stocked, A = Active, S = Picked
           AND org.ad_org_id = p_AD_Org_ID
           AND client.ad_client_id = p_AD_Client_ID
         GROUP BY pa.P_Asset_acct,
                  wh.C_Activity_ID,
                  wh.M_Warehouse_ID,
                  hus.M_Product_ID,
                  hus.C_UOM_ID,
                  schema.c_acctschema_id,
                  COALESCE(getCurrentCost(hus.M_Product_ID,
                                          p_keydate::date,
                                          schema.c_acctschema_id,
                                          costElement.m_costelement_id,
                                          p_AD_Client_ID,
                                          p_AD_org_ID), 0::numeric)
         HAVING first_agg(hutl.huStatus ORDER BY hutl.created DESC) NOT IN ('P', 'D', 'E')
         UNION
         SELECT pa.P_Asset_acct                                   AS C_ValidCombination_ID,
                wh.C_Activity_ID,
                wh.M_Warehouse_ID,
                p.M_Product_ID,
                p.C_UOM_ID,
                SUM(t.Movementqty)                                AS qty,
                COALESCE(getCurrentCost(p.M_Product_ID,
                                        p_keydate::date,
                                        schema.c_acctschema_id,
                                        costElement.m_costelement_id,
                                        p_AD_Client_ID,
                                        p_AD_org_ID), 0::numeric) AS CostPrice
         FROM M_Warehouse wh
                  JOIN ad_org org
                       on wh.ad_org_id = org.ad_org_ID
                  JOIN AD_Client client on wh.ad_client_ID = client.ad_client_id
                  JOIN ad_clientinfo clientInfo on client.ad_client_id = clientInfo.ad_client_id
                  JOIN c_acctschema schema on clientInfo.c_acctschema1_id = schema.c_acctschema_id
                  JOIN m_costelement costElement
                       on schema.costingmethod = costElement.costingmethod and costElement.isactive = 'Y' /* There should only be one active M_CostElement
              entry for a certain CostingMethod because of the unique index M_CostElement_CostingMethod_Unique. Please, keep this function in sync with this index */

                  LEFT OUTER JOIN M_Locator l ON wh.M_Warehouse_ID = l.M_Warehouse_ID AND l.isActive = 'Y'
                  LEFT OUTER JOIN M_Transaction t ON l.M_Locator_ID = t.M_Locator_ID AND t.isActive = 'Y'
                  LEFT OUTER JOIN M_Product p ON t.M_Product_ID = p.M_Product_ID AND p.isActive = 'Y'
                  LEFT OUTER JOIN C_UOM uom ON p.C_UOM_ID = uom.C_UOM_ID AND uom.isActive = 'Y'
                  LEFT OUTER JOIN M_Product_acct pa ON p.M_Product_ID = pa.M_Product_ID AND pa.isActive = 'Y'
         WHERE p.M_Product_Category_ID =
               getSysConfigAsNumeric('PackingMaterialProductCategoryID'
                   , wh.AD_Client_ID
                   , wh.AD_Org_ID)
           AND t.MovementDate::date <= $1
           AND org.ad_org_id = p_AD_Org_ID
         GROUP BY pa.P_Asset_acct,
                  wh.C_Activity_ID,
                  wh.M_Warehouse_ID,
                  p.M_Product_ID,
                  p.C_UOM_ID,
                  schema.c_acctschema_id,
                  COALESCE(getCurrentCost(p.M_Product_ID,
                                          p_keydate::date,
                                          schema.c_acctschema_id,
                                          costElement.M_CostElement_ID,
                                          p_AD_Client_ID,
                                          p_AD_org_ID), 0::numeric)
     ) dat
         LEFT OUTER JOIN M_Product p
                         ON dat.M_Product_ID = p.M_Product_ID AND p.isActive = 'Y' and p.AD_Org_ID = p_ad_org_id
         LEFT OUTER JOIN M_Product_Trl pt
                         ON p.M_Product_ID = pt.M_Product_ID AND pt.ad_language = $5 AND pt.isActive = 'Y' AND
                            pt.AD_Org_ID = p_ad_org_id
         LEFT OUTER JOIN M_Warehouse wh
                         ON dat.M_Warehouse_ID = wh.M_Warehouse_ID AND wh.isActive = 'Y' AND wh.AD_Org_ID = p_ad_org_id
         LEFT OUTER JOIN C_Activity a ON dat.C_Activity_ID = a.C_Activity_ID AND a.isActive = 'Y'
         LEFT OUTER JOIN C_ValidCombination vc
                         ON dat.C_ValidCombination_ID = vc.C_ValidCombination_ID AND vc.isActive = 'Y'
         LEFT OUTER JOIN C_UOM uom ON dat.C_UOM_ID = uom.C_UOM_ID AND uom.isActive = 'Y'
         LEFT OUTER JOIN C_UOM_Trl uomt
                         ON uom.C_UOM_ID = uomt.C_UOM_ID AND uomt.ad_language = $5 AND uomt.isActive = 'Y'
WHERE qty != 0
  AND CASE WHEN $2 IS NULL THEN p.M_Product_ID ELSE $2 END = p.M_Product_ID
  AND CASE WHEN $3 IS NULL THEN wh.M_Warehouse_ID ELSE $3 END = wh.M_Warehouse_ID
ORDER BY vc.combination,
         vc.description,
         a.name,
         Wh.name,
         p.value
    ;


$$
    LANGUAGE sql STABLE
;


--
-- select *
-- from report.M_Cost_CostPrice_Function('2020-09-16',
--                                       null,
--                                       null,
--                                       'Y',
--                                       'de_DE',
--                                       1000000,
--                                       1000000)







