package com.nego.simulator.protocol;

import com.nego.simulator.model.NegotiateProtocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportsProtocol {
    NegotiateProtocol value();
}
