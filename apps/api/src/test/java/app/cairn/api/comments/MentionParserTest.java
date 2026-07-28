package app.cairn.api.comments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for @mention extraction (C3). Pure parsing; resolution to members is tested elsewhere. */
class MentionParserTest {

    @Test
    void noMentions() {
        MentionParser.Mentions m = MentionParser.parse("just a plain comment, nothing to see");
        assertThat(m.isEmpty()).isTrue();
        assertThat(m.userIds()).isEmpty();
        assertThat(m.emails()).isEmpty();
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(MentionParser.parse(null).isEmpty()).isTrue();
        assertThat(MentionParser.parse("").isEmpty()).isTrue();
    }

    @Test
    void parsesUuidMention() {
        UUID id = UUID.fromString("0190a0aa-1111-7222-8333-444455556666");
        MentionParser.Mentions m = MentionParser.parse("hey @" + id + " please review");
        assertThat(m.userIds()).containsExactly(id);
        assertThat(m.emails()).isEmpty();
    }

    @Test
    void parsesEmailMention() {
        MentionParser.Mentions m = MentionParser.parse("cc @ada@acme.test on this");
        assertThat(m.emails()).containsExactly("ada@acme.test");
        assertThat(m.userIds()).isEmpty();
    }

    @Test
    void lowercasesAndDedupesEmails() {
        MentionParser.Mentions m = MentionParser.parse("@Ada@Acme.Test and again @ada@acme.test");
        assertThat(m.emails()).containsExactly("ada@acme.test");
    }

    @Test
    void dedupesRepeatedUuid() {
        UUID id = UUID.fromString("0190a0aa-1111-7222-8333-444455556666");
        MentionParser.Mentions m = MentionParser.parse("@" + id + " and @" + id);
        assertThat(m.userIds()).containsExactly(id);
    }

    @Test
    void mixedUuidAndEmail() {
        UUID id = UUID.fromString("0190a0aa-1111-7222-8333-444455556666");
        MentionParser.Mentions m = MentionParser.parse("@" + id + " ping @bob@x.io too");
        assertThat(m.userIds()).containsExactly(id);
        assertThat(m.emails()).containsExactly("bob@x.io");
    }

    @Test
    void plainEmailWithoutAtMarkerIsNotAMention() {
        // "foo@bar.com" written in prose (no leading @ marker) must not be treated as a mention.
        MentionParser.Mentions m = MentionParser.parse("email me at foo@bar.com when done");
        assertThat(m.isEmpty()).isTrue();
    }
}
