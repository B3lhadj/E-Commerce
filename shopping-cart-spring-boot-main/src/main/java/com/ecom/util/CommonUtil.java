package com.ecom.util;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;



@Component
public class CommonUtil {


    @Autowired
    private UserService userService;







    public UserDtls getLoggedInUserDetails(Principal p) {
        String email = p.getName();
        UserDtls userDtls = userService.getUserByEmail(email);
        return userDtls;
    }


}
