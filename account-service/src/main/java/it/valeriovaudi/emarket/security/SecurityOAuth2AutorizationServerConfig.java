package it.valeriovaudi.emarket.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;

/**
 * Created by mrflick72 on 03/05/17.
 */

@Configuration
@EnableAuthorizationServer
public class SecurityOAuth2AutorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private AccountUserDetailsService accountUserDetailsService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints.authenticationManager(authenticationManager)
                .approvalStoreDisabled()
//                .tokenStore(tokenStore())
                .userDetailsService(accountUserDetailsService);
    }

/*    @Bean
    public RedisTokenStore tokenStore(){
        JdkSerializationStrategy jdkSerializationStrategy = new JdkSerializationStrategy();
        RedisTokenStore redisTokenStore = new RedisTokenStore(redisConnectionFactory);
        redisTokenStore.setSerializationStrategy(jdkSerializationStrategy);
        return redisTokenStore;
    }*/


    @Override
    public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {
        oauthServer.tokenKeyAccess("permitAll()")
                .checkTokenAccess("isAuthenticated()")
                .allowFormAuthenticationForClients();
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory()
                .withClient("my-trusted-client")
                .authorizedGrantTypes("client_credentials", "password", "authorization_code", "refresh_token", "implicit")
                .authorities("ROLE_USER")
                .scopes("read", "write", "trust","openid")
                .resourceIds("oauth2-resource")
                .autoApprove(true)
                .accessTokenValiditySeconds(200)
        .and()
                .withClient("employee-trusted-client")
                .authorizedGrantTypes("client_credentials", "password", "authorization_code", "refresh_token", "implicit")
                .authorities("ROLE_EMPLOYEE")
                .scopes("read", "write", "trust","openid")
                .resourceIds("oauth2-resource")
                .autoApprove(true)
                .accessTokenValiditySeconds(200);
    }
}
