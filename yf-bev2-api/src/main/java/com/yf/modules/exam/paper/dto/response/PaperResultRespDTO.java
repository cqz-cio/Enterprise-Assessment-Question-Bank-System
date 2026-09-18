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
    private boolean resultAvailable;

    public static PaperResultRespDTO from(PaperDTO paper) {
        boolean available = Integer.valueOf(1).equals(paper.getHandState())
                && !"PENDING".equals(paper.getGradingState()) && paper.getPassed() != null;
        return PaperResultRespDTO.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .handState(paper.getHandState())
                .resultAvailable(available)
                .passed(available ? paper.getPassed() : null)
                .build();
    }
}
