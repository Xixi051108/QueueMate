package com.queuemate.testsupport;

import com.queuemate.common.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("e2e")
@ConditionalOnProperty(
        prefix = "queuemate.test-support",
        name = "enabled",
        havingValue = "true"
)
@Service
public class PostmanTestDataCleanupService {

    private static final long FIXTURE_VENUE_ID = 4002L;
    private static final long ADJUSTMENT_USER_ID = 3001L;

    private final JdbcTemplate jdbcTemplate;

    public PostmanTestDataCleanupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PostmanCleanupResponse cleanup(PostmanCleanupRequest request) {
        String runId = request.runId();
        String username = "qm_" + runId;
        Long userId = findSafeTestUser(username);
        Long merchantId = findRequiredFixtureUser("merchant_tea");
        Long venueId = parseOptionalId(request.venueId());
        List<Long> slotIds = parseIds(request.slotIds());

        validateVenue(runId, venueId, merchantId);
        validateSlots(slotIds, merchantId);

        CleanupCounts counts = new CleanupCounts();
        cleanupAdminAdjustments(runId, counts);

        Set<QueueKey> queueKeys = new LinkedHashSet<>();
        if (userId != null) {
            queueKeys.addAll(findQueueKeys("user_id = ?", userId));
        }
        if (venueId != null) {
            queueKeys.addAll(findQueueKeys("venue_id = ?", venueId));
        }

        Set<Long> bookingIds = new LinkedHashSet<>();
        if (userId != null) {
            bookingIds.addAll(queryIds("select id from bookings where user_id = ?", userId));
        }
        if (!slotIds.isEmpty()) {
            bookingIds.addAll(queryIds(
                    "select id from bookings where slot_id in (" + placeholders(slotIds) + ")",
                    slotIds.toArray()
            ));
        }
        if (venueId != null) {
            bookingIds.addAll(queryIds("select id from bookings where venue_id = ?", venueId));
        }

        if (!bookingIds.isEmpty()) {
            counts.vouchersDeleted += jdbcTemplate.update(
                    "delete from booking_vouchers where booking_id in (" + placeholders(bookingIds) + ")",
                    bookingIds.toArray()
            );
            counts.bookingsDeleted += jdbcTemplate.update(
                    "delete from bookings where id in (" + placeholders(bookingIds) + ")",
                    bookingIds.toArray()
            );
        }

        if (userId != null) {
            counts.vouchersDeleted += jdbcTemplate.update(
                    "delete from booking_vouchers where user_id = ?",
                    userId
            );
            counts.walletTransactionsDeleted += jdbcTemplate.update(
                    "delete from wallet_transactions where user_id = ?",
                    userId
            );
            counts.queueTicketsDeleted += jdbcTemplate.update(
                    "delete from queue_tickets where user_id = ?",
                    userId
            );
        }

        if (venueId != null) {
            counts.vouchersDeleted += jdbcTemplate.update(
                    "delete from booking_vouchers where venue_id = ?",
                    venueId
            );
            counts.queueTicketsDeleted += jdbcTemplate.update(
                    "delete from queue_tickets where venue_id = ?",
                    venueId
            );
            List<Long> venueSlotIds = queryIds(
                    "select id from booking_slots where venue_id = ?",
                    venueId
            );
            slotIds = mergeIds(slotIds, venueSlotIds);
        }

        if (!slotIds.isEmpty()) {
            counts.slotsDeleted += jdbcTemplate.update(
                    "delete from booking_slots where id in (" + placeholders(slotIds) + ")",
                    slotIds.toArray()
            );
        }

        for (QueueKey key : queueKeys) {
            restoreQueueSequence(key);
        }

        if (venueId != null) {
            jdbcTemplate.update("delete from queue_daily_sequences where venue_id = ?", venueId);
            counts.venuesDeleted += jdbcTemplate.update("delete from venues where id = ?", venueId);
        }

        if (userId != null) {
            jdbcTemplate.update(
                    "delete from merchant_applications where applicant_id = ? or reviewer_id = ?",
                    userId,
                    userId
            );
            jdbcTemplate.update("delete from user_roles where granted_by = ?", userId);
            jdbcTemplate.update("delete from user_roles where user_id = ?", userId);
            jdbcTemplate.update("delete from wallets where user_id = ?", userId);
            counts.usersDeleted += jdbcTemplate.update("delete from users where id = ?", userId);
        }

        int remainingArtifacts = countRemainingArtifacts(
                username,
                venueId,
                slotIds,
                runId
        );

        return new PostmanCleanupResponse(
                runId,
                counts.usersDeleted,
                counts.venuesDeleted,
                counts.slotsDeleted,
                counts.bookingsDeleted,
                counts.vouchersDeleted,
                counts.queueTicketsDeleted,
                counts.walletTransactionsDeleted,
                counts.adjustmentsDeleted,
                remainingArtifacts
        );
    }

    private int countRemainingArtifacts(
            String username,
            Long venueId,
            List<Long> slotIds,
            String runId
    ) {
        int remaining = requiredCount(
                "select count(*) from users where username = ?",
                username
        );
        if (venueId != null) {
            remaining += requiredCount("select count(*) from venues where id = ?", venueId);
        }
        if (!slotIds.isEmpty()) {
            remaining += requiredCount(
                    "select count(*) from booking_slots where id in (" + placeholders(slotIds) + ")",
                    slotIds.toArray()
            );
        }
        remaining += requiredCount(
                """
                        select count(*)
                        from wallet_transactions
                        where user_id = ?
                          and type = 'ADJUSTMENT'
                          and remark in (?, ?)
                        """,
                ADJUSTMENT_USER_ID,
                "Postman admin adjustment " + runId,
                "Postman admin adjustment cleanup " + runId
        );
        return remaining;
    }

    private Long findSafeTestUser(String username) {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from users where username = ?",
                Integer.class,
                username
        );
        if (total == null || total == 0) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                """
                        select id
                        from users
                        where username = ?
                          and display_name = 'Postman User'
                          and phone = '13800007777'
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                username
        );
        if (ids.size() != total) {
            throw unsafe("拒绝清理不符合 Postman 标记的用户");
        }
        return ids.getFirst();
    }

    private Long findRequiredFixtureUser(String username) {
        List<Long> ids = queryIds(
                "select id from users where username = ? and status = 'ACTIVE'",
                username
        );
        if (ids.size() != 1) {
            throw unsafe("本地测试商家账号不存在或状态异常");
        }
        return ids.getFirst();
    }

    private void validateVenue(String runId, Long venueId, Long merchantId) {
        if (venueId == null) {
            return;
        }
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from venues where id = ?",
                Integer.class,
                venueId
        );
        if (total == null || total == 0) {
            return;
        }
        Integer safe = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from venues
                        where id = ?
                          and merchant_id = ?
                          and name in (?, ?)
                          and description in ('created by Postman', 'updated by Postman')
                        """,
                Integer.class,
                venueId,
                merchantId,
                "Postman Venue " + runId,
                "Postman Venue " + runId + " Updated"
        );
        if (safe == null || safe != total) {
            throw unsafe("拒绝清理不属于当前 Postman 运行的地点");
        }
    }

    private void validateSlots(List<Long> slotIds, Long merchantId) {
        if (slotIds.isEmpty()) {
            return;
        }
        Object[] args = slotIds.toArray();
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from booking_slots where id in (" + placeholders(slotIds) + ")",
                Integer.class,
                args
        );
        Integer safe = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from booking_slots
                        where id in (%s)
                          and venue_id = %d
                          and created_by = ?
                          and created_at >= current_timestamp - interval 2 day
                        """.formatted(placeholders(slotIds), FIXTURE_VENUE_ID),
                Integer.class,
                append(args, merchantId)
        );
        if (total == null || safe == null || !total.equals(safe)) {
            throw unsafe("拒绝清理不属于当前 Postman 运行的预约时段");
        }
    }

    private void cleanupAdminAdjustments(String runId, CleanupCounts counts) {
        String addRemark = "Postman admin adjustment " + runId;
        String restoreRemark = "Postman admin adjustment cleanup " + runId;
        List<AdjustmentRow> adjustments = jdbcTemplate.query(
                """
                        select id, balance_before, balance_after
                        from wallet_transactions
                        where user_id = ?
                          and type = 'ADJUSTMENT'
                          and remark in (?, ?)
                        for update
                        """,
                (rs, rowNum) -> new AdjustmentRow(
                        rs.getLong("id"),
                        rs.getBigDecimal("balance_before"),
                        rs.getBigDecimal("balance_after")
                ),
                ADJUSTMENT_USER_ID,
                addRemark,
                restoreRemark
        );
        if (adjustments.isEmpty()) {
            return;
        }

        BigDecimal netChange = adjustments.stream()
                .map(row -> row.balanceAfter().subtract(row.balanceBefore()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (netChange.signum() != 0) {
            int restored = jdbcTemplate.update(
                    """
                            update wallets
                            set balance = balance - ?
                            where user_id = ?
                              and balance - ? >= 0
                            """,
                    netChange,
                    ADJUSTMENT_USER_ID,
                    netChange
            );
            if (restored != 1) {
                throw unsafe("管理员余额无法安全恢复，已终止清理");
            }
        }

        List<Long> ids = adjustments.stream().map(AdjustmentRow::id).toList();
        counts.adjustmentsDeleted += jdbcTemplate.update(
                "delete from wallet_transactions where id in (" + placeholders(ids) + ")",
                ids.toArray()
        );
    }

    private List<QueueKey> findQueueKeys(String predicate, Object value) {
        return jdbcTemplate.query(
                "select distinct venue_id, queue_date from queue_tickets where " + predicate,
                (rs, rowNum) -> new QueueKey(
                        rs.getLong("venue_id"),
                        rs.getDate("queue_date")
                ),
                value
        );
    }

    private void restoreQueueSequence(QueueKey key) {
        Integer maxQueueNo = jdbcTemplate.queryForObject(
                "select max(queue_no) from queue_tickets where venue_id = ? and queue_date = ?",
                Integer.class,
                key.venueId(),
                key.queueDate()
        );
        if (maxQueueNo == null) {
            jdbcTemplate.update(
                    "delete from queue_daily_sequences where venue_id = ? and queue_date = ?",
                    key.venueId(),
                    key.queueDate()
            );
            return;
        }
        jdbcTemplate.update(
                "update queue_daily_sequences set last_no = ? where venue_id = ? and queue_date = ?",
                maxQueueNo,
                key.venueId(),
                key.queueDate()
        );
    }

    private List<Long> parseIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        try {
            return rawIds.stream().map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException ex) {
            throw unsafe("测试数据 ID 格式不合法");
        }
    }

    private Long parseOptionalId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(rawId);
        } catch (NumberFormatException ex) {
            throw unsafe("测试地点 ID 格式不合法");
        }
    }

    private List<Long> queryIds(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
    }

    private int requiredCount(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private List<Long> mergeIds(Collection<Long> first, Collection<Long> second) {
        Set<Long> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return new ArrayList<>(merged);
    }

    private String placeholders(Collection<?> values) {
        return String.join(",", java.util.Collections.nCopies(values.size(), "?"));
    }

    private Object[] append(Object[] values, Object last) {
        Object[] result = java.util.Arrays.copyOf(values, values.length + 1);
        result[values.length] = last;
        return result;
    }

    private BusinessException unsafe(String message) {
        return new BusinessException(HttpStatus.CONFLICT, "TEST_DATA_UNSAFE", message);
    }

    private record AdjustmentRow(Long id, BigDecimal balanceBefore, BigDecimal balanceAfter) {
    }

    private record QueueKey(Long venueId, Date queueDate) {
    }

    private static final class CleanupCounts {
        private int usersDeleted;
        private int venuesDeleted;
        private int slotsDeleted;
        private int bookingsDeleted;
        private int vouchersDeleted;
        private int queueTicketsDeleted;
        private int walletTransactionsDeleted;
        private int adjustmentsDeleted;
    }
}
