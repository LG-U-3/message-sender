package com.example.messagesender.service.template;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TemplateValueResolverOptions {

  private final boolean strict; // 허용되지 않은 변수 존재 시 실패 여부
  private final boolean allowMissing; // 필수 변수 누락 허용 여부
  private final boolean allowExtra; // 명시되지 않은 변수 허용 여부

  /**
   * message_templates.variables_json 기준으로 허용된 변수만 values에서 남길지 여부
   */
  @Builder.Default
  private boolean filterByVariablesJson = true;

  /**
   * billing_settlements.detail_json을 flatten 해서 커스텀 템플릿 변수로 제공할지 여부 예: {"a":{"b":1}} -> a.b = "1"
   */
  @Builder.Default
  private boolean flattenDetailJson = true;

  /**
   * admin-web 화면에서 쓰는 별칭 키도 함께 제공할지 여부 예: targetMonth -> billingMonth, totalPrice -> amount
   */
  @Builder.Default
  private boolean provideAliases = true;

  @Builder.Default
  private boolean strictTypeCheck = true; //

  /**
   * 기본 템플릿용 옵션
   */
  public static TemplateValueResolverOptions defaultOptions() {
    return TemplateValueResolverOptions.builder().strict(false).allowMissing(true).allowExtra(true)
        .build();
  }

  /**
   * 커스텀 템플릿용 옵션
   */
  public static TemplateValueResolverOptions strictOptions() {
    return TemplateValueResolverOptions.builder().strict(true).allowMissing(false).allowExtra(false)
        .build();
  }
}
