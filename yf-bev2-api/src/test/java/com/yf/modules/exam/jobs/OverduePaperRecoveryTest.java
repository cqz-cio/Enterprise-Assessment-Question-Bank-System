package com.yf.modules.exam.jobs;
import com.yf.modules.exam.paper.mapper.PaperMapper;
import com.yf.modules.exam.paper.service.PaperService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.mockito.Mockito.*;
class OverduePaperRecoveryTest {
    @Test void failedPaperDoesNotStopOthersAndWillBeRetriedOnNextSweep() {
        var mapper=mock(PaperMapper.class);var service=mock(PaperService.class);
        when(mapper.selectOverdueIds("")).thenReturn(List.of("bad","good"));
        doThrow(new IllegalStateException("simulated failure")).when(service).handPaper("bad");
        var recovery=new OverduePaperRecovery(mapper,service);recovery.recover();recovery.recover();
        verify(service,times(2)).handPaper("good");verify(service,times(2)).handPaper("bad");
    }
    @Test void fullFailingBatchDoesNotStarveLaterPapers() {
        var mapper=mock(PaperMapper.class);var service=mock(PaperService.class);
        var ids=IntStream.range(0,100).mapToObj(i->String.format("%03d",i)).toList();
        when(mapper.selectOverdueIds("")).thenReturn(ids);
        when(mapper.selectOverdueIds("099")).thenReturn(List.of("100"));
        var recovery=new OverduePaperRecovery(mapper,service);recovery.recover();recovery.recover();
        verify(service).handPaper("100");
    }
}
