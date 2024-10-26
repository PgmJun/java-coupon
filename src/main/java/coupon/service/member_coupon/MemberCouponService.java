package coupon.service.member_coupon;

import coupon.config.cache.RedisKey;
import coupon.domain.coupon.Coupon;
import coupon.domain.coupon.repository.CouponRepository;
import coupon.domain.member.repository.MemberRepository;
import coupon.domain.member_coupon.MemberCoupon;
import coupon.domain.member_coupon.repository.MemberCouponRepository;
import coupon.exception.MemberCouponIssueLimitException;
import coupon.service.coupon.dto.CouponIssueResponse;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCouponService {

    private static final String COUPON_ISSUE_COUNT_UP_SCRIPT =
            "local value = redis.call('GET', KEYS[1]); " +
                    "if not value then " +
                    "    value = 0; " +
                    "    redis.call('SET', KEYS[1], value); " +
                    "end; " +
                    "if tonumber(value) >= 5 then " +
                    "    return '-1'; " +
                    "else " +
                    "    value = tonumber(value) + 1; " +
                    "    redis.call('SET', KEYS[1], value); " +
                    "    return tostring(value); " +
                    "end";

    private final CouponRepository couponRepository;
    private final MemberRepository memberRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final RedisTemplate redisTemplate;

    @Transactional
    public CouponIssueResponse issueMemberCoupon(long memberId, long couponId) {
        memberRepository.getById(memberId);

        MemberCoupon issuedMemberCoupon = MemberCoupon.issue(memberId, getCoupon(couponId));
        memberCouponRepository.save(issuedMemberCoupon);
        updateIssuedMemberCouponCount(memberId, couponId);

        return CouponIssueResponse.from(issuedMemberCoupon);
    }

    private int updateIssuedMemberCouponCount(long memberId, long couponId) {
        String key = RedisKey.MEMBER_COUPON_COUNT.getKey(memberId, couponId);
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(COUPON_ISSUE_COUNT_UP_SCRIPT);
        script.setResultType(String.class);

        String result = (String) redisTemplate.execute(script, Collections.singletonList(key));
        if(result.equals("-1")) {
            throw new MemberCouponIssueLimitException();
        }

        return Integer.parseInt(result);
    }

    private Coupon getCoupon(long couponId) {
        String couponKey = RedisKey.COUPON.getKey(couponId);
        Coupon coupon = (Coupon) redisTemplate.opsForValue().get(couponKey);
        if(coupon == null) {
            coupon = couponRepository.getById(couponId);
        }

        return coupon;
    }
}