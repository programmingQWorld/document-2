Rest微服务工程构建
    构建步骤
        cloud-provider-payment8001
            建cloud-provider-payment8001
                创建完成后请回到父工程查看pom文件变化
            改POM
            server:
                port: 8001

                
            写YML
                spring:
                    application:
                        name: cloud-payment-service
                    datasource:
                    type: com.alibaba.druid.pool.DruidDataSource # 当前数据源操作类型
                    driver-class-name: org.gjt.mm.mysql.Driver # mysql驱动包 com.mysql.jdbc.Driver
                    
            主启动
        
        微服务提供者支付Module模块
        热部署Devtools
        cloud-consumer-order80
        微服务消费者订单Module模块
        工程重构
    目前工程样图