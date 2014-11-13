package com.sunlights.trade.service.impl;

import com.sunlights.account.service.AccountService;
import com.sunlights.account.service.CapitalService;
import com.sunlights.account.service.impl.AccountServiceImpl;
import com.sunlights.account.service.impl.CapitalServiceImpl;
import com.sunlights.account.vo.AcctChangeFlowVo;
import com.sunlights.account.vo.HoldCapitalVo;
import com.sunlights.account.vo.TotalCapitalInfo;
import com.sunlights.common.MsgCode;
import com.sunlights.common.Severity;
import com.sunlights.common.exceptions.BusinessRuntimeException;
import com.sunlights.common.utils.CommonUtil;
import com.sunlights.common.utils.DBHelper;
import com.sunlights.common.vo.Message;
import com.sunlights.common.vo.PageVo;
import com.sunlights.core.service.BankCardService;
import com.sunlights.core.service.OpenAccountPactService;
import com.sunlights.core.service.ProductService;
import com.sunlights.core.service.impl.BankCardServiceImpl;
import com.sunlights.core.service.impl.OpenAccountPactServiceImpl;
import com.sunlights.core.service.impl.PaymentService;
import com.sunlights.core.service.impl.ProductServiceImpl;
import com.sunlights.core.vo.BankCardVo;
import com.sunlights.customer.service.impl.CustomerService;
import com.sunlights.customer.vo.CustomerVo;
import com.sunlights.trade.dal.TradeDao;
import com.sunlights.trade.dal.impl.TradeDaoImpl;
import com.sunlights.trade.service.TradeService;
import com.sunlights.trade.vo.CapitalProductTradeVo;
import com.sunlights.trade.vo.TradeFormVo;
import com.sunlights.trade.vo.TradeSearchFormVo;
import com.sunlights.trade.vo.TradeVo;
import models.CustomerSession;
import models.Fund;
import models.HoldCapital;
import models.Trade;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * <p>Project: fsp</p>
 * <p>Title: TradeServiceImpl.java</p>
 * <p>Description: </p>
 * <p>Copyright (c) 2014 Sunlights.cc</p>
 * <p>All Rights Reserved.</p>
 *
 * @author <a href="mailto:jiaming.wang@sunlights.cc">wangJiaMing</a>
 */
public class TradeServiceImpl implements TradeService {
    private TradeDao tradeDao = new TradeDaoImpl();
    private CustomerService customerService = new CustomerService();
    private CapitalService capitalService = new CapitalServiceImpl();
    private OpenAccountPactService openAccountPactService = new OpenAccountPactServiceImpl();
    private AccountService accountService = new AccountServiceImpl();
    private ProductService productService = new ProductServiceImpl();
    private BankCardService bankCardService = new BankCardServiceImpl();
    private PaymentService paymentService = new PaymentService();

    @Override
    public List<TradeVo> getTradeListByToken(String token, TradeSearchFormVo tradeSearchFormVo, PageVo pageVo){
        CustomerSession customerSession = customerService.getCustomerSession(token);
        List<TradeVo> list = tradeDao.getTradeListByCustomerId(customerSession.getCustomerId(), tradeSearchFormVo,pageVo);
        return list;
    }

    public CapitalProductTradeVo findCapitalProductDetailTrade(String token, TradeSearchFormVo tradeSearchFormVo){
        CommonUtil.getInstance().validateParams(tradeSearchFormVo.getPrdType(), tradeSearchFormVo.getPrdCode());
        PageVo pageVo = new PageVo();
        pageVo.setPageSize(3);
        List<TradeVo> list = getTradeListByToken(token, tradeSearchFormVo, pageVo);
        HoldCapitalVo holdCapitalVo = capitalService.findCapitalProductDetail(tradeSearchFormVo.getPrdType(), tradeSearchFormVo.getPrdCode());

        CapitalProductTradeVo capitalProductTradeVo = new CapitalProductTradeVo();
        capitalProductTradeVo.setList(list);
        capitalProductTradeVo.setHoldCapitalVo(holdCapitalVo);
        capitalProductTradeVo.setTradeCount(pageVo.getCount());

        return capitalProductTradeVo;
    }


    public TotalCapitalInfo tradeFundOrder(TradeFormVo tradeFormVo, String token){
        String bankCardNo = tradeFormVo.getBankCardNo();
        String prdType = tradeFormVo.getPrdType();
        String mobilePhoneNo = tradeFormVo.getMobilePhoneNo();
        String deviceNo = tradeFormVo.getDeviceNo();
        String prdCode = tradeFormVo.getPrdCode();
        String tradeAmount = tradeFormVo.getTradeAmount();
        String quantity = tradeFormVo.getQuantity();

        CommonUtil.getInstance().validateParams(bankCardNo, prdCode, prdType, mobilePhoneNo, deviceNo,tradeAmount,quantity);
        tradeValidate(mobilePhoneNo, deviceNo);

        CustomerSession customerSession = customerService.getCustomerSession(token);
        String customerId = customerSession.getCustomerId();

        PageVo pageVo = new PageVo();
        pageVo.put("EQS_bankCardNo", bankCardNo);
        List<BankCardVo> bankCardVoList = bankCardService.findBankCardsByToken(token, pageVo);
        if (bankCardVoList == null || bankCardVoList.isEmpty()) {
            throw new BusinessRuntimeException(new Message(Severity.ERROR, MsgCode.BANK_CARD_NOT_BINGING));
        }
        BankCardVo bankCardVo = bankCardVoList.get(0);
        Fund fund = productService.findFundByCode(prdCode);

        //开户
        openAccountPactService.createFundOpenAccount(customerId, bankCardVo);
        //子帐号
        accountService.createSubAccount(customerId,"HXYH", prdType);//TODO
        //下单记录
        Trade trade = createTrade(tradeFormVo, bankCardVo, customerId, fund, "1");
        //调用支付接口
        boolean paymentFlag = paymentService.payment();
        if (!paymentFlag) {
            trade.setPayStatus("3");//付款失败
            trade.setTradeStatus("3");
            trade.setConfirmStatus("5");
            updateTrade(trade);
            return null;
        }

        //帐户变更记录
        HoldCapital holdCapital = saveAccountChangeInfo(prdType, prdCode, customerId, trade);
        //交易记录更新
        trade.setPayStatus("2");
        trade.setTradeStatus("2");
        trade.setConfirmStatus("4");
        trade.setHoldCapitalId(holdCapital.getId());
        updateTrade(trade);
        TotalCapitalInfo totalCapitalInfo = capitalService.getTotalCapital(mobilePhoneNo, true);

        return totalCapitalInfo;
    }

    private void tradeValidate(String mobilePhoneNo, String deviceNo) {
        CustomerVo customerVo = customerService.getCustomerVoByPhoneNo(mobilePhoneNo, deviceNo);
        if ("0".equals(customerVo.getCertify())) {
            throw new BusinessRuntimeException(new Message(Severity.ERROR, MsgCode.BANK_NAME_CERTIFY_FAIL));
        }
        if ("0".equals(customerVo.getBankCardCount())) {
            throw new BusinessRuntimeException(new Message(Severity.ERROR, MsgCode.BANK_CARD_NOT_BINGING));
        }
    }

    private HoldCapital saveAccountChangeInfo(String prdType, String prdCode, String customerId, Trade trade) {
        AcctChangeFlowVo acctChangFlowVo = new AcctChangeFlowVo();
        acctChangFlowVo.setCustomerId(customerId);
        acctChangFlowVo.setPrdCode(prdCode);
        acctChangFlowVo.setPrdName(trade.getProductName());
        acctChangFlowVo.setPrdType(prdType);
        acctChangFlowVo.setTradeType(trade.getType());
        acctChangFlowVo.setAmount(trade.getTradeAmount().abs());
        accountService.dealAccount(acctChangFlowVo);
        //持有资产，子帐号资产变更
        return capitalService.createHoldCapital(acctChangFlowVo);
    }

    private Trade updateTrade(Trade trade){
        trade.setUpdateTime(DBHelper.getCurrentTime());
        return tradeDao.updateTrade(trade);
    }

    private Trade createTrade(TradeFormVo tradeFormVo, BankCardVo bankCardVo, String customerId, Fund fund, String type){
        String tradeAmount = tradeFormVo.getTradeAmount();
        String quantity = tradeFormVo.getQuantity();
        String prdCode = tradeFormVo.getPrdCode();
        String bankName = bankCardVo.getBankName();
        String bankCardNo = bankCardVo.getBankCardNo();

        Timestamp currentTime = DBHelper.getCurrentTime();

        Trade trade = new Trade();
        trade.setTradeNo(generateTradeNo());
        if ("1".equals(type)) {//申购
            trade.setType("1");//
            trade.setTradeAmount(new BigDecimal(tradeAmount));
        }else{
            trade.setType("2");//赎回
            trade.setTradeAmount(new BigDecimal(tradeAmount).negate());
        }
        trade.setTradeStatus("1");//申购中
        trade.setConfirmStatus("1");//1-待确认
        trade.setTradeTime(currentTime);
        trade.setCreateTime(currentTime);
        trade.setCustId(customerId);
        trade.setBankCardNo(bankCardNo);
        trade.setBankName(bankName);
        trade.setPayStatus("1");//未付款
        trade.setQuantity(Integer.valueOf(quantity));

        trade.setProductCode(prdCode);
        trade.setProductName(fund.getChiName());
        trade.setProductPrice(fund.getMinApplyAmount());
        trade.setFee(fund.getCharge() == null ? BigDecimal.ZERO : fund.getCharge());
        return tradeDao.saveTrade(trade);
    }


    private String generateTradeNo(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssss");
        String time = sdf.format(DBHelper.getCurrentTime());
        return time + tradeDao.getTradeNoSeq();
    }


    public TotalCapitalInfo tradeFundRedeem(TradeFormVo tradeFormVo, String token){
        String prdType = tradeFormVo.getPrdType();
        String mobilePhoneNo = tradeFormVo.getMobilePhoneNo();
        String deviceNo = tradeFormVo.getDeviceNo();
        String prdCode = tradeFormVo.getPrdCode();
        String tradeAmount = tradeFormVo.getTradeAmount();
        String quantity = tradeFormVo.getQuantity();

        CommonUtil.getInstance().validateParams(prdCode, prdType, mobilePhoneNo, deviceNo,tradeAmount,quantity);
        tradeValidate(mobilePhoneNo, deviceNo);

        CustomerSession customerSession = customerService.getCustomerSession(token);
        String customerId = customerSession.getCustomerId();

        List<BankCardVo> bankCardVoList = bankCardService.findBankCardsByToken(token, new PageVo());//TODO 暂时只支持一张银行卡
        if (bankCardVoList == null || bankCardVoList.isEmpty()) {
            throw new BusinessRuntimeException(new Message(Severity.ERROR, MsgCode.BANK_CARD_NOT_BINGING));
        }
        BankCardVo bankCardVo = bankCardVoList.get(0);
        Fund fund = productService.findFundByCode(prdCode);

        //赎回记录
        Trade trade = createTrade(tradeFormVo, bankCardVo, customerId, fund, "2");
        //还款接口
        boolean paymentFlag = paymentService.payment();
        if (!paymentFlag) {
            trade.setPayStatus("3");//付款失败
            trade.setTradeStatus("3");
            trade.setConfirmStatus("5");
            updateTrade(trade);
            return null;
        }

        //帐户变更记录
        HoldCapital holdCapital = saveAccountChangeInfo(prdType, prdCode, customerId, trade);
        //交易记录更新
        trade.setPayStatus("2");
        trade.setTradeStatus("2");
        trade.setConfirmStatus("4");
        trade.setHoldCapitalId(holdCapital.getId());
        updateTrade(trade);
        TotalCapitalInfo totalCapitalInfo = capitalService.getTotalCapital(mobilePhoneNo, true);

        return totalCapitalInfo;
    }



}
