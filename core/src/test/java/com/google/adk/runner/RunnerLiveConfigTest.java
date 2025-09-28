/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.runner;

import static com.google.common.truth.Truth.assertThat;

import com.google.adk.agents.LiveRequest;
import com.google.adk.agents.LiveRequestQueue;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.adk.testing.TestLlm;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.Content;
import com.google.genai.types.LiveConnectConfig;
import com.google.genai.types.Modality;
import com.google.genai.types.Part;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RunnerLiveConfigTest {

  @Test
  public void singleAgentLive_doesNotAutoDefaultModalitiesOrTranscription() {
    TestLlm testLlm =
        new TestLlm(ImmutableList.of(LlmResponse.builder().turnComplete(true).build()));

    LlmAgent rootAgent = LlmAgent.builder().name("root_agent").model(testLlm).build();
    InMemoryRunner runner = new InMemoryRunner(rootAgent);

    LiveRequestQueue live = new LiveRequestQueue();
    live.send(LiveRequest.builder().content(Content.fromParts(Part.fromText("hello"))).build());

    Session session = runner.sessionService().createSession("test-app", "test-user").blockingGet();
    List<?> events =
        runner.runLive(session, live, RunConfig.builder().build()).toList().blockingGet();
    assertThat(events).isNotNull();

    LiveConnectConfig liveCfg = testLlm.getLastRequest().liveConnectConfig();
    assertThat(liveCfg).isNotNull();
    assertThat(liveCfg.responseModalities().get()).isEmpty();
    assertThat(liveCfg.outputAudioTranscription()).isEmpty();
    assertThat(liveCfg.inputAudioTranscription()).isEmpty();
  }

  @Test
  public void multiAgentLive_appliesDefaultsAndTranscription() {
    TestLlm testLlm =
        new TestLlm(ImmutableList.of(LlmResponse.builder().turnComplete(true).build()));

    LlmAgent child = LlmAgent.builder().name("child").model(testLlm).build();
    LlmAgent rootAgent =
        LlmAgent.builder().name("root_agent").model(testLlm).subAgents(child).build();
    InMemoryRunner runner = new InMemoryRunner(rootAgent);

    LiveRequestQueue live = new LiveRequestQueue();
    live.send(
        LiveRequest.builder()
            .blob(
                Part.fromBytes("audio".getBytes(StandardCharsets.UTF_8), "audio/pcm")
                    .inlineData()
                    .get())
            .build());

    Session session = runner.sessionService().createSession("test-app", "test-user").blockingGet();
    List<?> events =
        runner.runLive(session, live, RunConfig.builder().build()).toList().blockingGet();
    assertThat(events).isNotNull();

    LiveConnectConfig liveCfg = testLlm.getLastRequest().liveConnectConfig();
    assertThat(liveCfg).isNotNull();
    assertThat(liveCfg.responseModalities().get())
        .containsExactly(new Modality(Modality.Known.AUDIO));
    assertThat(liveCfg.outputAudioTranscription()).isPresent();
    assertThat(liveCfg.inputAudioTranscription()).isPresent();
  }
}
