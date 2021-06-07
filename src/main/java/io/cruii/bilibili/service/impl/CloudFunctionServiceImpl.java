package io.cruii.bilibili.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import io.cruii.bilibili.config.TencentApiConfig;
import io.cruii.bilibili.dto.BilibiliUserDTO;
import io.cruii.bilibili.entity.BilibiliUser;
import io.cruii.bilibili.entity.CloudFunctionLog;
import io.cruii.bilibili.mapper.BilibiliUserMapper;
import io.cruii.bilibili.service.CloudFunctionService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpCookie;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author cruii
 * Created on 2021/6/6
 */
@Service
@Log4j2
public class CloudFunctionServiceImpl implements CloudFunctionService {

    private final TencentApiConfig apiConfig;

    private final BilibiliUserMapper bilibiliUserMapper;

    public CloudFunctionServiceImpl(TencentApiConfig apiConfig, BilibiliUserMapper bilibiliUserMapper) {
        this.apiConfig = apiConfig;
        this.bilibiliUserMapper = bilibiliUserMapper;
    }

    @Override
    public List<CloudFunctionLog> listLogs(String username, long startTime, long endTime) throws TencentCloudSDKException {
        return CollUtil.newArrayList();
    }

    @Override
    public List<BilibiliUserDTO> listFunctions() {
        List<BilibiliUser> bilibiliUsers = bilibiliUserMapper.selectList(null);

        return bilibiliUsers.stream()
                .map(b -> {
                    String sessdataCookie = "SESSDATA=" + b.getSessdata() + "; Path=/;";
                    String body = HttpRequest.get("https://api.bilibili.com/x/web-interface/nav")
                            .cookie(sessdataCookie)
                            .execute().body();
                    JSONObject data = JSONUtil.parseObj(body).getJSONObject("data");
                    InputStream avatarStream = HttpRequest.get(data.getStr("face"))
                            .execute().bodyStream();
                    StringBuilder sb = new StringBuilder();
                    String username = data.getStr("uname");
                    for (int i = 0; i < username.length(); i++) {
                        if (i > 0 && i < username.length() - 1) {
                            sb.append("*");
                        } else {
                            sb.append(username.charAt(i));
                        }
                    }
                    HttpCookie sessdata = new HttpCookie("SESSDATA", b.getSessdata());
                    HttpCookie dedeUserID = new HttpCookie("DedeUserID", b.getDedeuserid());
                    sessdata.setDomain("account.bilibili.com");
                    dedeUserID.setDomain("account.bilibili.com");
                    String coinResp = HttpRequest.get("https://account.bilibili.com/site/getCoin")
                            .cookie(sessdata, dedeUserID)
                            .execute().body();
                    Double coins = JSONUtil.parseObj(coinResp).getJSONObject("data").getDouble("money");

                    JSONObject levelInfo = data.getJSONObject("level_info");
                    return BilibiliUserDTO.builder()
                            .dedeuserid(b.getDedeuserid())
                            .username(sb.toString())
                            .avatar("data:image/jpeg;base64," + Base64.encode(avatarStream))
                            .coins(coins)
                            .level(levelInfo.getInt("current_level"))
                            .currentExp(levelInfo.getInt("current_exp"))
                            .nextExp(levelInfo.getInt("next_exp"))
                            .vipType(data.getJSONObject("vip").getInt("type"))
                            .key(SecureUtil.md5(b.getSessdata())).build();
                }).collect(Collectors.toList());
    }
}
