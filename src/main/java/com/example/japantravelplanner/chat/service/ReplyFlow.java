package com.example.japantravelplanner.chat.service;

/**
 * AI 답이 대화를 끊지 않게 한다.
 * 일정을 만들 준비가 되기 전에는 답이 질문으로 끝나야 하는데, AI가 "취향이 잘 전달되었습니다!"처럼
 * 끝맺어 버린 적이 있다 (10-10). 그러면 서버가 다음 질문을 붙인다.
 */
final class ReplyFlow {

    static final String FOLLOW_UP = "이 밖에 여행에서 꼭 하고 싶은 것이나 신경 쓰이는 점이 있으세요?";

    private ReplyFlow() {
    }

    /**
     * @param ready         AI가 일정을 만들 준비가 됐다고 판단했는지
     * @param wantsGenerate 사용자가 일정을 만들어 달라고 했는지
     */
    static String keepGoing(String reply, boolean ready, boolean wantsGenerate) {
        if (reply == null || ready || wantsGenerate) return reply;
        String trimmed = reply.strip();
        if (trimmed.endsWith("?") || trimmed.endsWith("？") || trimmed.endsWith("?)")) return reply;
        // 질문이 중간에만 있고 끝은 마무리 인사인 경우도 대화가 끊긴 것처럼 보이므로 끝에 질문을 붙인다
        return trimmed + "\n\n" + FOLLOW_UP;
    }
}
