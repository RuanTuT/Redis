package com.hmdp.utils;


import com.aliyun.teaopenapi.models.Config;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import static com.aliyun.teautil.Common.toJSONString;


public class SmsService {

    // 替换成你的阿里云 AccessKey 和 AccessSecret
    private static final String accessKeyId = System.getenv("ALIYUN_ACCESS_KEY_ID");
    private static final String accessKeySecret = System.getenv("ALIYUN_ACCESS_KEY_SECRET");
    public static Client createClient() throws Exception {
        Config config = new Config()
                // 配置 AccessKey ID，请确保代码运行环境设置了环境变量。
                .setAccessKeyId(accessKeyId)
                // 配置 AccessKey Secret，请确保代码运行环境设置了环境变量。
                .setAccessKeySecret(accessKeySecret);
        // System.getenv()方法表示获取系统环境变量，请配置环境变量后，在此填入环境变量名称，不要直接填入AccessKey信息。

        // 配置 Endpoint
        config.endpoint = "dysmsapi.aliyuncs.com";

        return new Client(config);
    }
    public static void sendSms(String phoneNumber, String code) throws Exception {
        Client client = createClient();
        // 构造请求对象，请填入请求参数值
        SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setPhoneNumbers(phoneNumber)
                .setSignName("阿里云短信测试")
                .setTemplateCode("SMS_154950909")
                .setTemplateParam("{\"code\":\"" + code + "\"}");

        // 获取响应对象
        SendSmsResponse sendSmsResponse = client.sendSms(sendSmsRequest);

        // 响应包含服务端响应的 body 和 headers
        System.out.println(toJSONString(sendSmsResponse));
    }

}
