package com.example.messagesender.service.template;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.example.messagesender.domain.billing.BillingSettlement;
import com.example.messagesender.domain.message.MessageReservation;
import com.example.messagesender.domain.message.MessageSendResult;
import com.example.messagesender.domain.message.MessageTemplate;
import com.example.messagesender.repository.BillingSettlementRepository;
import com.example.messagesender.repository.ChargedHistoryRepository; //추가
import com.example.messagesender.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TemplateValueResolver {

  private final UserRepository userRepository;
  private final BillingSettlementRepository billingSettlementRepository;
  private final ObjectMapper objectMapper;

  private final ChargedHistoryRepository chargedHistoryRepository; //추가

  private static final DecimalFormat MONEY = new DecimalFormat("#,###");

  /**
   * 템플릿 치환에 사용할 values Map 생성
   */
  public Map<String, String> resolve(MessageSendResult sendResult, MessageTemplate template) {

    Map<String, String> values = new HashMap<>();

    // 1) User 값 세팅
    var user = userRepository.findById(sendResult.getUserId())
        .orElseThrow(() -> new IllegalArgumentException("유저 없음: userId=" + sendResult.getUserId()));

    values.put("userName", safe(user.getName()));
    values.put("email", safe(user.getEmail()));
    values.put("phone", safe(user.getPhone()));

    // 2) Reservation -> targetMonth
    MessageReservation reservation = sendResult.getReservation();
    if (reservation == null) {
      throw new IllegalArgumentException(
          "예약(reservation) 없음: messageSendResultId=" + sendResult.getId());
    }

    String targetMonth = reservation.getTargetMonth(); // "2026-01"
    values.put("targetMonth", safe(targetMonth));

    // 2-1) 할인금액(charged_histories) 합계 values에 추가 //추가
    ChargedHistoryRepository.DiscountSum ds =
        chargedHistoryRepository.sumDiscountsByUserAndMonth(sendResult.getUserId(), targetMonth); //추가

    long contractSum = ds == null ? 0L : ds.getContractSum();
    long bundledSum  = ds == null ? 0L : ds.getBundledSum(); 
    long premierSum  = ds == null ? 0L : ds.getPremierSum(); 
    long discountTotal = contractSum + bundledSum + premierSum; 

    values.put("contractDiscountPrice", formatMoney(contractSum)); 
    values.put("bundledDiscountPrice", formatMoney(bundledSum));  
    values.put("premierDiscountPrice", formatMoney(premierSum));  
    values.put("totalDiscount", formatMoney(discountTotal)); 

 // 3) Settlement -> detail_json merge
    BillingSettlement settlement =
        billingSettlementRepository.findByUserIdAndTargetMonth(sendResult.getUserId(), targetMonth)
            .orElseThrow(() -> new IllegalArgumentException(
                "정산서 없음: userId=" + sendResult.getUserId() + ", month=" + targetMonth));

    mergeDetailJson(settlement.getDetailJson(), values);

    // //추가: detail_json에 totalAmount/finalAmount 키가 없으면 0으로 들어가서, 최종 청구액(final_amount)으로 보정
    String finalAmt = formatMoney(settlement.getFinalAmount());

    // totalPrice가 비었거나(혹은 0) 이면 최종 청구액으로 세팅
    String totalPrice = values.get("totalPrice");
    if (totalPrice == null || totalPrice.isBlank() || "0".equals(totalPrice) || "0,000".equals(totalPrice)) {
      values.put("totalPrice", finalAmt);
    }

    // finalPrice / finalAmount는 없으면 보정 (이미 있으면 유지)
    values.putIfAbsent("finalPrice", finalAmt);
    values.putIfAbsent("finalAmount", finalAmt);
    values.putIfAbsent("totalAmount", finalAmt); // totalAmount도 일단 동일하게 맞춰서 0 노이즈 제거

    return values;
  }

  private void mergeDetailJson(String detailJson, Map<String, String> values) {
    if (detailJson == null || detailJson.isBlank())
      return;

    try {
      JsonNode root = objectMapper.readTree(detailJson);

      long totalAmount = root.path("totalAmount").asLong(0);
      long finalAmount = root.path("finalAmount").asLong(0);

      values.put("totalPrice", formatMoney(totalAmount));
      values.put("finalPrice", formatMoney(finalAmount));

      // 원본 키도 혹시 템플릿에서 쓸 수 있으니 같이 제공(선택)
      values.putIfAbsent("totalAmount", formatMoney(totalAmount));
      values.putIfAbsent("finalAmount", formatMoney(finalAmount));

      // discounts 배열 -> 텍스트로 합치기
      if (root.has("discounts") && root.get("discounts").isArray()) {
        values.put("discountsText", joinLines(root.get("discounts")));
      }

      // - final_amount / total_amount 같은 스네이크 케이스도 들어올 수 있으니 fallback
      if (!values.containsKey("totalPrice") || values.get("totalPrice").isBlank()) {
        long fallback = root.path("total_amount").asLong(0);
        if (fallback > 0)
          values.put("totalPrice", formatMoney(fallback));
      }
      if (!values.containsKey("finalPrice") || values.get("finalPrice").isBlank()) {
        long fallback = root.path("final_amount").asLong(0);
        if (fallback > 0)
          values.put("finalPrice", formatMoney(fallback));
      }

    } catch (Exception e) {
      throw new IllegalStateException("detail_json 파싱 실패", e);
    }
  }

  /**
   * variablesJson 최상위 key만 허용 변수로 보고 values를 필터링
   */
  private void keepOnlyTemplateVariables(String variablesJson, Map<String, String> values) {
    if (variablesJson == null || variablesJson.isBlank())
      return;

    try {
      JsonNode vars = objectMapper.readTree(variablesJson);
      if (!vars.isObject())
        return;

      Set<String> allowed = new HashSet<>();
      Iterator<String> it = vars.fieldNames();
      while (it.hasNext())
        allowed.add(it.next());

      values.keySet().retainAll(allowed);

    } catch (Exception e) {
      throw new IllegalStateException("variablesJson 파싱 실패", e);
    }
  }

  private String joinLines(JsonNode arr) {
    StringBuilder sb = new StringBuilder();
    for (JsonNode n : arr) {
      if (sb.length() > 0)
        sb.append("\n");
      sb.append(n.asText());
    }
    return sb.toString();
  }

  private String formatMoney(long v) {
    return MONEY.format(v);
  }

  private String safe(String v) {
    return v == null ? "" : v;
  }
}
