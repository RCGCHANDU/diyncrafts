package com.diyncrafts.web.app.transcoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FfprobeParserTest {

    @Test
    void readsDimensionsDurationAndAudio() {
        MediaInfo info = FfprobeParser.parse("""
                {"streams":[{"codec_type":"video","width":1920,"height":1080},{"codec_type":"audio"}],
                 "format":{"duration":"12.480000"}}""");
        assertThat(info).isEqualTo(new MediaInfo(1920, 1080, 12.48, true));
    }

    @Test
    void detectsMissingAudio() {
        MediaInfo info = FfprobeParser.parse("""
                {"streams":[{"codec_type":"video","width":640,"height":480}],"format":{}}""");
        assertThat(info.hasAudio()).isFalse();
        assertThat(info.duration()).isZero();
    }

    @Test
    void appliesRotationFromSideDataOrTags() {
        assertThat(FfprobeParser.parse("""
                {"streams":[{"codec_type":"video","width":1920,"height":1080,
                  "side_data_list":[{"side_data_type":"Display Matrix","rotation":-90}]}],"format":{}}"""))
                .extracting(MediaInfo::width, MediaInfo::height).containsExactly(1080, 1920);
        assertThat(FfprobeParser.parse("""
                {"streams":[{"codec_type":"video","width":1920,"height":1080,"tags":{"rotate":"270"}}],"format":{}}"""))
                .extracting(MediaInfo::width, MediaInfo::height).containsExactly(1080, 1920);
    }

    @Test
    void ignoresCoverArtAndRejectsAudioOnlyOrGarbage() {
        assertThatThrownBy(() -> FfprobeParser.parse("""
                {"streams":[{"codec_type":"audio"},{"codec_type":"video","width":500,"height":500,
                  "disposition":{"attached_pic":1}}],"format":{}}"""))
                .isInstanceOf(TranscodingException.class)
                .hasMessage("The uploaded file does not contain a video stream.");
        assertThatThrownBy(() -> FfprobeParser.parse("not json")).isInstanceOf(TranscodingException.class);
    }
}
