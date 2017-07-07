package it.valeriovaudi.emarket.integration;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import it.valeriovaudi.emarket.anticorruptation.account.AccountAnticorruptationLayerService;
import it.valeriovaudi.emarket.model.Customer;
import it.valeriovaudi.emarket.model.CustomerContact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Created by mrflick72 on 30/05/17.
 */

@Service
public class AccountIntegrationService extends AbstractIntegrationService {

    @Autowired
    private AccountAnticorruptationLayerService accountAnticorruptationLayerService;

    @Autowired
    private OAuth2RestTemplate accountIntegrationServiceRestTemplate;

    @Value("${external-service.base-uri-schema.account}")
    private String accountServiceUriSchema;

    @HystrixCommand(fallbackMethod = "getCustomerFormAccountDataFallback",
            commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public Customer getCustomerFormAccountData(String userName){
        ResponseEntity<String> serviceCall = serviceCall(userName);
        return accountAnticorruptationLayerService.newCustomer(serviceCall.getBody(),
                serviceCall.getHeaders().getContentType().toString());
    }

    @HystrixCommand(fallbackMethod = "getCustomerContactFormAccountDataFallback",
            commandProperties = {@HystrixProperty(name="execution.isolation.strategy", value="SEMAPHORE")})
    public CustomerContact getCustomerContactFormAccountData(String userName){
        ResponseEntity<String> serviceCall = serviceCall(userName);
        return accountAnticorruptationLayerService.newCustomerContact(serviceCall.getBody(),
                serviceCall.getHeaders().getContentType().toString());
    }

    private ResponseEntity<String> serviceCall(String userName) {
        URI uri = UriComponentsBuilder.fromHttpUrl(accountServiceUriSchema).buildAndExpand(userName).toUri();
        return accountIntegrationServiceRestTemplate.exchange(newRequestEntity(uri), String.class);
    }

    /**
     * fallback
     * */
    public CustomerContact getCustomerContactFormAccountDataFallback(String userName) {
        return null;
    }

    public Customer getCustomerFormAccountDataFallback(String userName) {
        return null;
    }
}
