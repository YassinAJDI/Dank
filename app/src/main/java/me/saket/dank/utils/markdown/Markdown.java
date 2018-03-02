package me.saket.dank.utils.markdown;

import android.text.Html;
import android.text.Spanned;

import com.nytimes.android.external.cache3.Cache;

import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Message;
import net.dean.jraw.models.Submission;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.exceptions.Exceptions;
import me.saket.dank.BuildConfig;
import me.saket.dank.ui.submission.PendingSyncReply;
import me.saket.dank.utils.JrawUtils;

/**
 * Handles converting Reddit's markdown into Spans that can be rendered by TextView.
 * <p>
 * TODO: Proivide using Dagger.
 * TODO: Use MarkdownHints through this class.
 */
@SuppressWarnings({ "StatementWithEmptyBody", "deprecation" })
public class Markdown {

  private static final boolean ESCAPE_FORWARD_SLASHES = true;
  private static final boolean IGNORE_ESCAPING_OF_FORWARD_SLASHES = false;

  private final DankHtmlTagHandler htmlTagHandler;
  private final Cache<String, CharSequence> cache;

  @Inject
  public Markdown(DankHtmlTagHandler htmlTagHandler, @Named("markdown") Cache<String, CharSequence> markdownCache) {
    this.htmlTagHandler = htmlTagHandler;
    this.cache = markdownCache;
  }

  private CharSequence parse(String textWithMarkdown, boolean escapeForwardSlashes) {
    Callable<CharSequence> valueSeeder = () -> {
      String htmlEscapedSource = Html.fromHtml(textWithMarkdown).toString();
      if (escapeForwardSlashes) {
        // Forward slashes need to be escaped. I don't know a better way to do this.
        // Converts ¯\\_(ツ)_/¯ -> ¯\_(ツ)_/¯.
        htmlEscapedSource = htmlEscapedSource.replaceAll(Matcher.quoteReplacement("\\\\"), Matcher.quoteReplacement("\\"));
      }

      try {
        String sourceWithCustomTags = htmlTagHandler.overrideTags(htmlEscapedSource);
        Spanned spanned = Html.fromHtml(sourceWithCustomTags, null, htmlTagHandler);
        return trimTrailingWhitespace(spanned);

      } catch (Exception e) {
        e.printStackTrace();
        return Html.fromHtml(htmlEscapedSource);
      }
    };
    try {
      return cache.get(textWithMarkdown, valueSeeder);
    } catch (ExecutionException e) {
      // Should never happen.
      throw Exceptions.propagate(e);
    }
  }

  public CharSequence parse(Message message) {
    return parse(JrawUtils.messageBodyHtml(message), IGNORE_ESCAPING_OF_FORWARD_SLASHES);
  }

  public CharSequence parse(Comment comment) {
    return parse(JrawUtils.commentBodyHtml(comment), IGNORE_ESCAPING_OF_FORWARD_SLASHES);
  }

  public CharSequence parse(PendingSyncReply reply) {
    return parse(reply.body(), ESCAPE_FORWARD_SLASHES);
  }

  public CharSequence parseSelfText(Submission submission) {
    return parse(JrawUtils.selfPostHtml(submission), IGNORE_ESCAPING_OF_FORWARD_SLASHES);
  }

  /**
   * Reddit sends escaped body: "JRAW is escaping html entities.\n\n&lt; &gt; &amp;"
   * instead of "JRAW is escaping html entities.\n\n< > &".
   * <p>
   * Convert "**Something**" -> "Something", without any styling.
   */
  public String stripMarkdown(String markdown) {
    // Since all styling is added using spans, converting the CharSequence to a String will remove all styling.
    String source = Html.fromHtml(markdown).toString();

    CharSequence result;
    try {
      String sourceWithCustomTags = htmlTagHandler.overrideTags(source);
      Spanned spanned = Html.fromHtml(sourceWithCustomTags, null, htmlTagHandler);
      result = trimTrailingWhitespace(spanned);

    } catch (Exception e) {
      e.printStackTrace();
      result = Html.fromHtml(source);
    }
    return result.toString();
  }

  private static CharSequence trimTrailingWhitespace(CharSequence source) {
    int len = source.length();
    while (--len >= 0 && Character.isWhitespace(source.charAt(len))) {
    }
    return source.subSequence(0, len + 1);
  }

  public void clearCache() {
    if (!BuildConfig.DEBUG) {
      throw new AssertionError();
    }
    cache.invalidateAll();
  }
}