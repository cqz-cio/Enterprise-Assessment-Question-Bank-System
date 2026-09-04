package com.yf.modules.exam.paper.dto.response;

import com.yf.modules.exam.paper.dto.PaperDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaperResultRespDTO {
    private String id;
    private String title;
    private Integer handState;
    private Boolean passed;

    public static PaperResultRespDTO from(PaperDTO paper) {
        return PaperResultRespDTO.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .handState(paper.getHandState())
                .passed(paper.getPassed())
                .build();
    }
}
