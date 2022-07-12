package io.xdag.core;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PoolConfig {
    double poolRation;
    double minerRewardRation;
    double fundRation;
    double directRation;
}
