package com.storeops.shared.security;

import com.storeops.shared.error.UnauthorizedError;
import com.storeops.staff.model.User;
import com.storeops.staff.service.StaffService;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves AuthenticatedUser controller arguments from the X-User-Id header (auth stub). */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-User-Id";

    private final StaffService staffService;

    public CurrentUserArgumentResolver(StaffService staffService) {
        this.staffService = staffService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(HEADER);
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedError("Missing " + HEADER + " header");
        }
        User user = staffService.findById(userId).orElseThrow(() -> new UnauthorizedError("Unknown user"));
        return new AuthenticatedUser(user.id(), user.storeId(), user.role());
    }
}
