package com.thoughtmechanix.licenses.services;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.thoughtmechanix.licenses.clients.OrganizationRestTemplateClient;
import com.thoughtmechanix.licenses.config.ServiceConfig;
import com.thoughtmechanix.licenses.model.License;
import com.thoughtmechanix.licenses.model.Organization;
import com.thoughtmechanix.licenses.repository.LicenseRepository;
import com.thoughtmechanix.licenses.utils.UserContextHolder;
import feign.Feign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class LicenseService {
    private static final Logger logger = LoggerFactory.getLogger(LicenseService.class);
    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    ServiceConfig config;

    @Autowired
    OrganizationRestTemplateClient organizationRestClient;


    public License getLicense(String organizationId,String licenseId) {
        License license = licenseRepository.findByOrganizationIdAndLicenseId(organizationId, licenseId);

        Organization org = getOrganization(organizationId);

        return license
                .withOrganizationName( org.getName())
                .withContactName( org.getContactName())
                .withContactEmail( org.getContactEmail() )
                .withContactPhone( org.getContactPhone() )
                .withComment(config.getExampleProperty());
    }

    /**
     * 断路器实现的原理 是 会维护一个定时器
     *
     * 服务使用到了 Hystrix 断路器，就是把对下游的请求 封装到了对应接口的 线程池中进行了调用
     * 通过包装在 一个独立于原始线程的线程中， 客户端不再 直接等待 调用完成
     *
     * 相反，断路器会监视线程，如果线程运行时间太久，线程池就可以终止该调用
     *
     * @HystrixCommand将java 类方法标记为 由 Hystrix断路器进行管理，当spring框架看到这个注解时，它将动态生成一个
     * 代理，该代理将包装该方法  并通过专门用于处理远程调用的 线程池来管理对该方法的 所有调用
     *
     *
     */
    @HystrixCommand  // 这里使用private方法 会不会生效呢？？？
    private Organization getOrganization(String organizationId) {
        return organizationRestClient.getOrganization(organizationId);
    }

    private void randomlyRunLong(){
      Random rand = new Random();

      int randomNum = rand.nextInt((3 - 1) + 1) + 1;

      if (randomNum==3) sleep();
    }

    private void sleep(){
        try {
            Thread.sleep(11000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 每当调用超过 1000ms 时，断路器将中断 对 getLicensesByOrg() 方法的调用
     *
     * commandProperties 属性允许 开发人员提供 附加的属性来定制Hystrix
     *
     * execution.isolation.thread.timeoutIn.Milliseconds 属性设置Hystrix 调用的 最大超时时间为 12s
     *
     * fallbackMethod：属性定义了类中的 一个方法 如果来自Hystrix的调用失败  那么就会调用该方法
     * threadPoolKey：属性定义线程池的唯一名称，告诉Hystrix 需要建立一个新的线程池
     * threadPoolProperties：属性用于定义和定制threadPool的行为
     * coreSize：属性用于定义线程池的最大数量
     * maxQueueSize：用于定义一个位于线程池前的队列，它可以对传入的请求进行排队
     */
    @HystrixCommand(//fallbackMethod = "buildFallbackLicenseList",
            threadPoolKey = "licenseByOrgThreadPool",
            threadPoolProperties =
                    {@HystrixProperty(name = "coreSize",value="30"),
                     @HystrixProperty(name="maxQueueSize", value="10")},
            // commandProperties：主要用于配置断路器的行为
            commandProperties={
                    // 用于控制Hystrix 考虑将该断路器跳闸之前  在 10s 之内必须发生的 连续调用数量
                     @HystrixProperty(name="circuitBreaker.requestVolumeThreshold", value="10"),
                // 是在超过 上面属性设置值 之后 在断路器跳闸之前必须达到的调用失败(由于超时、抛出异常或返回HTTP 500)百分比
                     @HystrixProperty(name="circuitBreaker.errorThresholdPercentage", value="75"),
                // 是在断路器跳闸之后，Hystrix允许另一个调用通过以便查看服务 是否健康之前 Hystrix的休眠时间
                     @HystrixProperty(name="circuitBreaker.sleepWindowInMilliseconds", value="7000"),
                //（设置中断超时时间 以毫秒为单位） 用于控制Hystrix用来监视服务调用问题的窗口大小  其默认值10000ms = 10s
                     @HystrixProperty(name="metrics.rollingStats.timeInMilliseconds", value="15000"),
                // 控制在定义的滚动窗口中 收集统计信息的次数
                     @HystrixProperty(name="metrics.rollingStats.numBuckets", value="5")}
    )
    public List<License> getLicensesByOrg(String organizationId){
        logger.debug("LicenseService.getLicensesByOrg  Correlation id: {}", UserContextHolder.getContext().getCorrelationId());
        randomlyRunLong();

        return licenseRepository.findByOrganizationId(organizationId);

    }

    // 在后备方法中 返回一个硬编码的值
    private List<License> buildFallbackLicenseList(String organizationId){
        List<License> fallbackList = new ArrayList<>();
        License license = new License()
                .withId("0000000-00-00000")
                .withOrganizationId( organizationId )
                .withProductName("Sorry no licensing information currently available");

        fallbackList.add(license);
        return fallbackList;
    }

    public void saveLicense(License license){
        license.withId( UUID.randomUUID().toString());

        licenseRepository.save(license);
    }

    public void updateLicense(License license){
      licenseRepository.save(license);
    }

    public void deleteLicense(License license){
        licenseRepository.delete( license.getLicenseId());
    }

}
