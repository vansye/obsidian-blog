package luv.vansye.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验签。**这个类里每一条都对应一种「错了也看不出来」的写法。**
 *
 * 用例 ① 用的是 GitHub 官方文档里那组已知向量，不是我自己算一个再拿它断言自己。
 * 自产自销的向量测不出「hex 忘了小写」「前缀写成 sha1=」「算法选了 HmacSHA1」
 * 这类错——两边都错的话断言照样绿。外部向量是唯一能钉住「和 GitHub 对得上」的东西。
 *
 * 用例 ⑤ 是这里最要紧的一条：**密钥没配时必须拒绝，而不是放行。**
 * 「没配就放行」让本机开发很顺手，代价是部署时忘设环境变量的那一刻
 * 端点对全世界敞开，而且没有任何征兆。
 */
class WebhookSignatureTest {

    // GitHub 文档 "Validating webhook deliveries" 里给的例子
    private static final String DOC_SECRET = "It's a Secret to Everybody";
    private static final String DOC_PAYLOAD = "Hello, World!";
    private static final String DOC_SIGNATURE =
            "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("① GitHub 官方向量：算出来的签名和文档给的一字不差")
    void matchesGithubsDocumentedVector() {
        WebhookSignature sig = new WebhookSignature(DOC_SECRET);

        // 这一条同时钉住了三件事：算法是 HmacSHA256、hex 是小写、前缀是 sha256=
        assertThat(sig.sign(bytes(DOC_PAYLOAD))).isEqualTo(DOC_SIGNATURE);
        assertThat(sig.verify(bytes(DOC_PAYLOAD), DOC_SIGNATURE)).isTrue();
    }

    @Test
    @DisplayName("② 载荷改一个字节，签名就不该通过")
    void rejectsTamperedPayload() {
        WebhookSignature sig = new WebhookSignature(DOC_SECRET);

        assertThat(sig.verify(bytes("Hello, World?"), DOC_SIGNATURE))
                .as("载荷被改过还能过，验签就等于没做")
                .isFalse();
    }

    @Test
    @DisplayName("③ 换个密钥算出来的签名不通过 —— 密钥真的参与了运算")
    void rejectsSignatureFromAnotherSecret() {
        String forged = new WebhookSignature("wrong-secret").sign(bytes(DOC_PAYLOAD));

        assertThat(new WebhookSignature(DOC_SECRET).verify(bytes(DOC_PAYLOAD), forged)).isFalse();
    }

    @Test
    @DisplayName("④ 签名头缺失（null）→ 拒绝，不是抛异常")
    void rejectsMissingHeader() {
        WebhookSignature sig = new WebhookSignature(DOC_SECRET);

        // 抛异常的话控制器会变成 500，而 GitHub 对 5xx 是重投——
        // 一个没带签名头的请求就能让它反复重投。必须是干净的 false
        assertThat(sig.verify(bytes(DOC_PAYLOAD), null)).isFalse();
        assertThat(sig.verify(null, DOC_SIGNATURE)).isFalse();
    }

    @Test
    @DisplayName("⑤ 密钥没配 → configured() 为假，且任何签名都验不过（fail-closed）")
    void failsClosedWhenSecretIsMissing() {
        // 攻击者能拿到的最好的东西：用他自己选的密钥算出的、格式完全正确的签名
        String forged = new WebhookSignature("attacker-guess").sign(bytes(DOC_PAYLOAD));

        for (String missing : new String[]{null, "", "   "}) {
            WebhookSignature sig = new WebhookSignature(missing);
            String label = missing == null ? "null" : "\"" + missing + "\"";

            assertThat(sig.configured()).as("密钥 %s 应该算「没配」", label).isFalse();
            assertThat(sig.verify(bytes(DOC_PAYLOAD), forged))
                    .as("密钥 %s 时必须拒绝格式正确的伪造签名", label).isFalse();
            assertThat(sig.verify(bytes(DOC_PAYLOAD), DOC_SIGNATURE))
                    .as("密钥 %s 时连真签名也必须拒绝——没配就是谁都不放", label).isFalse();
        }
    }

    @Test
    @DisplayName("⑤b 空密钥算不出 HMAC —— 「用空密钥伪造签名」这条路在 JCE 层面就堵死了")
    void cannotEvenComputeASignatureWithoutASecret() {
        // 第一版的 ⑤ 想测「攻击者知道密钥是空的，于是用空密钥算出正确签名」，
        // 结果 SecretKeySpec 直接抛 IllegalArgumentException: Empty key——
        // 那个攻击场景**不存在**。这条把这件事钉下来，免得以后有人
        // 「顺手」给 sign 加个空密钥兜底，反而把这条路打开
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new WebhookSignature("").sign(bytes(DOC_PAYLOAD)));
    }

    @Test
    @DisplayName("⑥ 前缀不对 → 拒绝（sha1= 是 GitHub 的旧头，不能顺带认）")
    void rejectsWrongPrefix() {
        WebhookSignature sig = new WebhookSignature(DOC_SECRET);
        String hexOnly = DOC_SIGNATURE.substring("sha256=".length());

        assertThat(sig.verify(bytes(DOC_PAYLOAD), hexOnly)).as("没有前缀").isFalse();
        assertThat(sig.verify(bytes(DOC_PAYLOAD), "sha1=" + hexOnly)).as("sha1 前缀").isFalse();
    }

    @Test
    @DisplayName("⑦ 大写 hex → 拒绝。GitHub 只发小写，放宽比较等于自己开口子")
    void rejectsUppercaseHex() {
        WebhookSignature sig = new WebhookSignature(DOC_SECRET);

        assertThat(sig.verify(bytes(DOC_PAYLOAD), DOC_SIGNATURE.toUpperCase())).isFalse();
    }

    @Test
    @DisplayName("⑧ 密钥两端的空白被吃掉 —— env 文件里的尾随换行不该让验签全挂")
    void trimsSurroundingWhitespaceInSecret() {
        // /etc/vansye-blog/env 里 `VAULT_WEBHOOK_SECRET=xxx` 后面多一个空格，
        // 或者从密码管理器复制时带上换行，都是很容易发生的事。
        // 那种情况下的症状是「所有 webhook 都 401」，和密钥填错完全一样
        WebhookSignature padded = new WebhookSignature("  " + DOC_SECRET + "\n");

        assertThat(padded.verify(bytes(DOC_PAYLOAD), DOC_SIGNATURE)).isTrue();
    }
}
