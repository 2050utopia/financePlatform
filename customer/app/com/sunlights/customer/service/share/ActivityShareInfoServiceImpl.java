package com.sunlights.customer.service.share;

import com.sunlights.customer.ActivityConstant;
import com.sunlights.customer.dal.CustomerDao;
import com.sunlights.customer.dal.impl.CustomerDaoImpl;
import com.sunlights.customer.factory.ActivityServiceFactory;
import com.sunlights.customer.service.ActivityService;
import com.sunlights.customer.vo.ShareInfoVo;
import models.Activity;
import models.Customer;
import models.ShareInfo;
import org.apache.commons.lang3.StringUtils;
import play.Logger;

import java.text.MessageFormat;

/**
 * Created by tangweiqun on 2014/12/17.
 */
public class ActivityShareInfoServiceImpl extends AbstractShareInfoService {
    private CustomerDao customerDao = new CustomerDaoImpl();

    private ActivityService activityService = ActivityServiceFactory.getActivityService();

    @Override
    public void prepareShareInfo(ShareInfoContext context) {
        Activity activity = activityService.getByUnknowCondition(context.getRefId());
        ShareInfo shareInfo = context.getShareInfo();
        ShareInfoVo shareInfoVo = context.getShareInfoVo();
        String activityTitle = activity.getTitle();
        Logger.debug("activityTitle == " + activityTitle);
        String descTemplate = shareInfo.getContent();
        shareInfoVo.setContent(MessageFormat.format(descTemplate, activityTitle));
    }

    @Override
    public String getLongUrl(ShareInfoContext context) {
        Logger.debug("ActivityShareInfoServiceImpl getLongUrl call");
        ShareInfo shareInfo = context.getShareInfo();
        StringBuilder sb = new StringBuilder();
        sb.append(shareInfo.getBaseUrl());
        String mobile = getMobile(context.getCustNo());
        if (Integer.valueOf(ActivityConstant.ACCOUNT_COMMON_ONE).equals(shareInfo.getRelateRefId())) {
            Activity activity = activityService.getByUnknowCondition(context.getRefId());
            sb.append("/" + activity.getUrl());
            //  sb.append(context.getCommonParamter());
            sb.append("?info=" + mobile + "|" + activity.getId());
        }
        return sb.toString();
    }

    /**
     * 获得手机号
     *
     * @return
     */
    private String getMobile(String custNo) {
        if (StringUtils.isEmpty(custNo)) {
            return "";
        }
        Customer customer = customerDao.getCustomerByCustomerId(custNo);
        String mobile = customer.getMobile();//获得手机号
        Logger.debug("获得的手机号为:" + mobile);
        return mobile;
    }
}