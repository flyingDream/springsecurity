package com.huan.springsecurity.conf;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.client.RestTemplate;

import com.huan.springsecurity.security.CustormAuthenticationFailureHandler;
import com.huan.springsecurity.security.CustormAuthenticationSuccessHandler;
import com.huan.springsecurity.security.CustormLogoutHandler;
import com.huan.springsecurity.security.CustormSecurityDecision;
import com.huan.springsecurity.security.LoginFilter;
import com.huan.springsecurity.security.UserDetailServiceImpl;
import com.huan.springsecurity.social.qq.QQLoginConfigure;

/**
 * spring security 的java配置
 * 
 * @描述
 * @作者 huan
 * @时间 2017年5月30日 - 下午2:24:57
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true) // 拦截方法
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
	@Autowired
	private AuthenticationManager authenticationManager;

	@Override
	public void configure(WebSecurity web) throws Exception {
		web.ignoring().antMatchers("/static/**"); // 不拦截静态资源
		web.ignoring().antMatchers("/css/**"); // 不拦截静态资源
	}

	@Bean
	public SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> qqLoginConfigure() {
		return new QQLoginConfigure();
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.apply(qqLoginConfigure()).and()//
				.formLogin()//
				.loginPage("/login.html") // 自定义登录界面
				.loginProcessingUrl("/auth") // 处理登录的请求
				.usernameParameter("user_name") // 登录表单的用户名的name的值
				.passwordParameter("pass_word") // 登录表单密码的name的值
				.successHandler(custormAuthenticationSuccessHandler())//
				.failureHandler(custormAuthenticationFailureHandler()).and()// 登录成功后的处理
				// 配置了这个拦截器之后上方配置的属性无效
				.addFilterAt(loginFilter(), UsernamePasswordAuthenticationFilter.class) // 替换掉spring security中的拦截器
				.userDetailsService(createUserDetailService())//
				.sessionManagement()//
				.maximumSessions(1)// 同一个账号同时只可一个用户登录
				.maxSessionsPreventsLogin(true)// 后登录的不可登入系统
				.and()//
				.and()//
				.logout()// 登出
				.logoutUrl("/logout")// 登出的url
				.addLogoutHandler(custormLogoutHandler())// 退出时可以处理的事情
				.clearAuthentication(true)//
				.invalidateHttpSession(true)//
				.and()//
				.csrf().disable()// 禁用csrf
				.authorizeRequests()//
				.accessDecisionManager(accessDecisionManager())// 自定义决策管理器
				.antMatchers("/hi", "/login.html", "/auth").permitAll()// 可以匿名访问
				// .anyRequest().authenticated(); // 需要认证才可以进行访问
				.anyRequest().access("@custormSecurityDecision.decision(authentication,request)")// 自定义授权策略
				.and()//
				.exceptionHandling()//
				.accessDeniedPage("/403.html"); // 没有权限访问
	}

	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(createUserDetailService())//
				.passwordEncoder(passwordEncoder())//
				.and()//
				.inMemoryAuthentication()//
				.withUser("admin").password("admin").roles("ADMIN");//
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	/**
	 * 自定义决策管理器
	 */
	@Bean
	public AccessDecisionManager accessDecisionManager() {
		List<AccessDecisionVoter<? extends Object>> decisionVoters = new ArrayList<AccessDecisionVoter<? extends Object>>();
		decisionVoters.add(webExpressionVoter());
		return new AffirmativeBased(decisionVoters); // 有一票通过则为通过
	}

	/** 表达式控制器 */
	@Bean
	public SecurityExpressionHandler<FilterInvocation> expressionHandler() {
		return new DefaultWebSecurityExpressionHandler();
	}

	/** 表达式投票器 */
	@Bean
	public AccessDecisionVoter<FilterInvocation> webExpressionVoter() {
		WebExpressionVoter voter = new WebExpressionVoter();
		voter.setExpressionHandler(expressionHandler());
		return voter;
	}

	@Bean
	public CustormSecurityDecision custormSecurityDecision() {
		return new CustormSecurityDecision();
	}

	@Bean
	public Filter loginFilter() {
		/**
		 * 当时用了自定义的LoginFilter之后，上方configure方法登录部分的配置将不会生效，需要重新配置
		 */
		LoginFilter filter = new LoginFilter("/auth");
		filter.setUsernameParameter("user_name");
		filter.setPasswordParameter("pass_word");
		filter.setAuthenticationSuccessHandler(custormAuthenticationSuccessHandler());
		filter.setAuthenticationFailureHandler(custormAuthenticationFailureHandler());
		filter.setAuthenticationManager(authenticationManager);
		return filter;
	}

	@Bean
	public LogoutHandler custormLogoutHandler() {
		return new CustormLogoutHandler();
	}

	@Bean
	public AuthenticationFailureHandler custormAuthenticationFailureHandler() {
		CustormAuthenticationFailureHandler authenticationFailureHandler = new CustormAuthenticationFailureHandler();
		authenticationFailureHandler.setDefaultFailureUrl("/login.html?error");
		return authenticationFailureHandler;
	}

	@Bean
	public AuthenticationSuccessHandler custormAuthenticationSuccessHandler() {
		CustormAuthenticationSuccessHandler authenticationSuccessHandler = new CustormAuthenticationSuccessHandler();
		authenticationSuccessHandler.setAlwaysUseDefaultTargetUrl(true);
		authenticationSuccessHandler.setDefaultTargetUrl("/index.html");
		return authenticationSuccessHandler;
	}

	@Bean
	public UserDetailsService createUserDetailService() {
		return new UserDetailServiceImpl(passwordEncoder());
	}

	/** 密码加密器 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
