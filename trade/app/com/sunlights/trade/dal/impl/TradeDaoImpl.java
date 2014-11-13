package com.sunlights.trade.dal.impl;

import com.google.common.base.Strings;
import com.sunlights.common.dal.EntityBaseDao;
import com.sunlights.common.dal.PageDao;
import com.sunlights.common.dal.impl.PageDaoImpl;
import com.sunlights.common.utils.ArithUtil;
import com.sunlights.common.utils.CommonUtil;
import com.sunlights.common.vo.PageVo;
import com.sunlights.trade.dal.TradeDao;
import com.sunlights.trade.vo.TradeSearchFormVo;
import com.sunlights.trade.vo.TradeVo;
import models.Trade;

import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Project: fsp</p>
 * <p>Title: TradeDaoImpl.java</p>
 * <p>Description: </p>
 * <p>Copyright (c) 2014 Sunlights.cc</p>
 * <p>All Rights Reserved.</p>
 *
 * @author <a href="mailto:jiaming.wang@sunlights.cc">wangJiaMing</a>
 */
public class TradeDaoImpl extends EntityBaseDao implements TradeDao {

    private PageDao pageDao = new PageDaoImpl();

    public String getTradeNoSeq() {
        Query query = em.createNativeQuery("SELECT nextval('trade_seq')");
        String trade_seq = query.getSingleResult().toString();
        return Strings.padStart(trade_seq, 4, '0');
    }


    @Override
    public List<TradeVo> getTradeListByCustomerId(String customerId, TradeSearchFormVo tradeSearchFormVo, PageVo pageVo) {
        String productCode = tradeSearchFormVo.getPrdCode();
        String productType = tradeSearchFormVo.getPrdType();

        String sql =  "select t " +
                "from  Trade t " +
                "where 1 = 1" +
                " /~ and t.custId = {customerId} ~/" +
                " /~ and t.productCode = {productCode} ~/" +
                " order by t.tradeTime desc";

        pageVo.put("EQS_customerId", customerId);
        pageVo.put("EQS_productCode", productCode);
        List<Trade> list = pageDao.findXsqlBy(sql, pageVo);

        return transTradeVo(list);
    }

    @Override
    public Trade saveTrade(Trade trade) {
        return create(trade);
    }

    @Override
    public Trade updateTrade(Trade trade) {
        return update(trade);
    }

    private List<TradeVo> transTradeVo(List<Trade> list) {
        List<TradeVo> tradeVos = new ArrayList<TradeVo>();
        for (Trade trade : list) {
            TradeVo tradeVo = new TradeVo();
            tradeVo.setPrdName(trade.getProductName());
            tradeVo.setPrdCode(trade.getProductCode());
            tradeVo.setTradeNo(trade.getTradeNo());
            tradeVo.setTradeTime(CommonUtil.dateToString(trade.getTradeTime(), CommonUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
            tradeVo.setTradeAmount(ArithUtil.bigToScale2(trade.getTradeAmount()));
            tradeVo.setTradeStatus(trade.getTradeStatus());
            tradeVo.setTradeType(trade.getType());

            tradeVos.add(tradeVo);
        }

        return tradeVos;
    }


}
