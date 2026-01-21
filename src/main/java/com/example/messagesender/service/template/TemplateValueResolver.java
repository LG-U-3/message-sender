package com.example.messagesender.service.template;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import com.example.messagesender.domain.billing.BillingSettlement;
import com.example.messagesender.domain.message.MessageReservation;
import com.example.messagesender.domain.message.MessageSendResult;
import com.example.messagesender.domain.message.MessageTemplate;
import com.example.messagesender.repository.BillingSettlementRepository;
import com.example.messagesender.repository.ChargedHistoryRepository;
import com.example.messagesender.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TemplateValueResolver {

  private final UserRepository userRepository;
  private final BillingSettlementRepository billingSettlementRepository;
  private final ObjectMapper objectMapper;

  // 생성자 시그니처 유지용 (외부 코드 수정 금지)
  @SuppressWarnings("unused")
  private final ChargedHistoryRepository chargedHistoryRepository;

  private static final DecimalFormat MONEY = new DecimalFormat("#,###");

  public Map<String, String> resolve(MessageSendResult sendResult, MessageTemplate template) {

    Map<String, String> values = new HashMap<>();

    // 1) User
    var user = userRepository.findById(sendResult.getUserId())
        .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

    values.put("userName", safe(user.getName()));
    values.put("email", safe(user.getEmail()));
    values.put("phone", safe(user.getPhone()));

    // 2) Reservation
    MessageReservation reservation = sendResult.getReservation();
    if (reservation == null) {
      throw new IllegalArgumentException("reservation 없음");
    }

    String targetMonth = reservation.getTargetMonth();
    values.put("targetMonth", safe(targetMonth));

    // UI alias
    values.put("billingMonth", safe(targetMonth));

    // 3) Settlement (정산서가 단일 진실 소스)
    BillingSettlement settlement = billingSettlementRepository
        .findByUserIdAndTargetMonth(sendResult.getUserId(), targetMonth)
        .orElseThrow(() -> new IllegalArgumentException("정산서 없음"));

    long finalAmount = settlement.getFinalAmount();
    values.put("finalAmount", formatMoney(finalAmount));
    values.put("amount", formatMoney(finalAmount)); // UI에서 쓰는 키

    // 3-2) detail_json 파싱해서 value 맵 구성 (현재 DB는 ARRAY 구조)
    String detailJson = settlement.getDetailJson();
    if (detailJson != null && !detailJson.isBlank()) {
      try {
        JsonNode root = objectMapper.readTree(detailJson);

        long usageAmount = 0L;
        long contract = 0L;
        long bundled = 0L;
        long premier = 0L;

        if (root.isArray()) {
          for (JsonNode item : root) {
            usageAmount += readLong(item.get("chargedPrice"));
            contract += readLong(item.get("contractDiscountPrice"));
            bundled += readLong(firstNonNull(item, "bundledDiscountPrice", "BundledDiscountPrice"));
            premier += readLong(firstNonNull(item, "premierDiscountPrice", "PremierDiscountPrice"));
          }
        } else if (root.isObject()) {
          usageAmount = readLong(firstNonNull(root, "usageAmount", "totalPrice", "chargedPrice"));
          contract = readLong(firstNonNull(root, "contractDiscountPrice", "ContractDiscountPrice"));
          bundled = readLong(firstNonNull(root, "bundledDiscountPrice", "BundledDiscountPrice"));
          premier = readLong(firstNonNull(root, "premierDiscountPrice", "PremierDiscountPrice"));
        }

        long discountAmount = contract + bundled + premier;

        // 표준 키 (기본 템플릿 + 커스텀 템플릿에서 공통 사용)
        values.put("usageAmount", formatMoney(usageAmount));
        values.put("discountAmount", formatMoney(discountAmount));

        // 기존 키 호환 (기본 템플릿용)
        values.put("totalPrice", formatMoney(usageAmount));
        values.put("chargedPrice", formatMoney(usageAmount));
        values.put("totalDiscount", formatMoney(discountAmount));

        values.put("contractDiscountPrice", formatMoney(contract));
        values.put("bundledDiscountPrice", formatMoney(bundled));
        values.put("premierDiscountPrice", formatMoney(premier));

        // 옵션: flattenDetailJson (커스텀에서 a.b 키 필요할 때)
        TemplateValueResolverOptions options = resolveOptions(template);
        if (options.isFlattenDetailJson()) {
          Map<String, String> flattened = new HashMap<>();
          new JsonFlattener().flatten(root, "detail", flattened);
          values.putAll(flattened);
        }

      } catch (Exception e) {
        throw new IllegalArgumentException("settlement.detail_json 파싱 실패", e);
      }
    }

    // ui상에서 깨지는걸 방지하기위해서 최소 안전장치 걸어둠
    values.putIfAbsent("usageAmount", formatMoney(finalAmount));
    values.putIfAbsent("totalPrice", formatMoney(finalAmount));
    values.putIfAbsent("discountAmount", formatMoney(0L));
    values.putIfAbsent("totalDiscount", formatMoney(0L));
    values.putIfAbsent("billingMonth", values.getOrDefault("targetMonth", ""));
    values.putIfAbsent("usageAmount", values.getOrDefault("totalPrice", values.getOrDefault("chargedPrice", "")));
    values.putIfAbsent("discountAmount", values.getOrDefault("totalDiscount", ""));

    // 4) variables_json 기반 커스텀 정책 적용
    TemplateValueResolverOptions options = resolveOptions(template);

    TemplateVariablesParser parser = new TemplateVariablesParser(objectMapper);
    var specMap = parser.parse(template.getVariablesJson());

    TemplateValuesValidator validator = new TemplateValuesValidator();
    validator.validateAndFilter(specMap, values, options);

    return values;
  }

  private TemplateValueResolverOptions resolveOptions(MessageTemplate template) {
    boolean isCustom = template.getVariablesJson() != null && !template.getVariablesJson().isBlank();
    return isCustom ? TemplateValueResolverOptions.strictOptions()
                    : TemplateValueResolverOptions.defaultOptions();
  }

  private JsonNode firstNonNull(JsonNode node, String... keys) {
    if (node == null) return null;
    for (String k : keys) {
      JsonNode v = node.get(k);
      if (v != null && !v.isNull() && !v.isMissingNode()) return v;
    }
    return null;
  }

  private long readLong(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull())
      return 0L;

    if (node.isNumber())
      return node.asLong();

    if (node.isTextual()) {
      String s = node.asText().replace(",", "").trim();
      if (s.isBlank())
        return 0L;
      try {
        return Long.parseLong(s);
      } catch (Exception e) {
        return 0L;
      }
    }
    return 0L;
  }

  private String formatMoney(long v) {
    return MONEY.format(v);
  }

  private String safe(String v) {
    return v == null ? "" : v;
  }
}
