package com.trout.cg.opt;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.GroupOps;
import java.math.BigInteger;

/** Pluggable exponentiation strategy (Owner: Person D). */
public interface ExpStrategy {
    Bqf exp(GroupOps ops, Bqf base, BigInteger k);
}
