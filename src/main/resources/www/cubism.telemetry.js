function TelemetryPlotter(selector, from, to) {
    
    var context = cubism.context()
        .step(3600000 / 300)
        .size(300);

    /*d3.select(selector).selectAll(".axis")
        .data(["bottom"])
        .enter().append("div")
        .attr("class", function(d) { return d + " axis"; })
        .each(function(d) { d3.select(this).call(context.axis().ticks(12).orient(d)); });*/
    
    d3.select(selector).append("div")
        .attr("class", "rule")
        .call(context.rule());
    
    
    d3.select(selector).selectAll(".horizon")
        .data([latency()])
    .enter().insert("div", ".caption")
        .attr("class", "horizon green-background")
        .call(context.horizon().extent([0, 10000]));
    
    d3.select(selector).append("div").attr("class", "caption").text(from);
    
    d3.select(selector).selectAll("empty")
        .data([telemetry("CPU", from), telemetry("MEM", from)])
    .enter().append("div", ".caption")
        .attr("class", "horizon green-background")
        .call(context.horizon().extent([0, 100]));
    
    d3.select(selector).append("div").attr("class", "caption").text(to);
    
    d3.select(selector).selectAll("empty")
        .data([telemetry("CPU", to), telemetry("MEM", to)])
    .enter().append("div", ".caption")
        .attr("class", "horizon green-background")
        .call(context.horizon().colors(["#ecf5fb", "#B7DCC3"]));
    
    d3.select(selector).selectAll(".axis")
        .data(["bottom"])
        .enter().append("div")
        .attr("class", function(d) { return d + " axis"; })
        .each(function(d) { d3.select(this).call(context.axis().ticks(3).orient(d)); });
    
    d3.select(selector).insert("div", ".horizon").attr("class", "caption").html(from + " &rarr; " + to);
    
    context.on("focus", function(i) {
        d3.selectAll(".value").style("right", i == null ? null : context.size() - i + "px");
    });
    

    function latency() {
        var last, values = [];
        return context.metric(function(start, stop, step, callback) {
            if (isNaN(last)) last = +start;
            stop = +stop;  
            var query = encodeURI(JSON.stringify({ source: from, dest: to, time: { $gt: last, $lte: stop } }));
            var sort = encodeURI(JSON.stringify({ time: -1 }));
        
            $.getJSON("http://localhost:9000/db/latency?q=" + query + "&sort=" + sort, function(data) {
                var ltn = data.map(function(item) { 
                    return {
                        x: item.time,
                        y: item.pingTotal
                    }
                }).reverse();
                values = toTs(last, stop, step, ltn, values);
                last = stop;
                callback(null, values = values.slice((start - stop) / step));    
            }).fail(function() {
                callback("Error", [0]);    
            });
        }, "Latency");
    }
    
    function telemetry(dataType, host) {
        var last, values = [];
        return context.metric(function(start, stop, step, callback) {
            if (isNaN(last)) last = +start;
            stop = +stop;  
            var query = encodeURI(JSON.stringify({ addr: host, timestamp: { $gt: last, $lte: stop }  }));
            var sort = encodeURI(JSON.stringify({ timestamp: -1 }));
            $.getJSON("http://localhost:9000/db/telemetry?q=" + query + "&sort=" + sort, function(data) {
                var ltn = data.map(dataMapper(dataType)).reverse();
                values = toTs(last, stop, step, ltn, values);
                last = stop;
                callback(null, values = values.slice((start - stop) / step));    
            }).fail(function() {
                callback("Error", [0]);    
            });
        }, dataType);
    }
    
    function dataMapper(dataType) {
        if (dataType == "CPU") {
            return function(item) {
                return {
                    x: item.timestamp,
                    y: Math.floor(item.cpu * 100)
                }
            }
        } else {
            return function(item) {
                return {
                    x: item.timestamp,
                    y: Math.floor(item.memory)
                }
            }
        }
    }
    
    function toTs(start, stop, step, arr, metrics) {
        while( start < stop ) {
            var endOfPArt = start + step;
            var max = 0;
            var threshold;
            while ( !!(threshold = arr.shift()) && threshold.x <= endOfPArt ) {
                if ( max < threshold.y ) max = threshold.y;
            }
        
            metrics.push(max);  
            start += step;
        }
        return metrics;
    }   
    
    this.stop = function() {
        context.stop();
    }
}    