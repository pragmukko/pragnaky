'use strict';

function authority() {
    var url = window.location.href;
    if (url.startsWith("file")) {
        //return "http://128.107.17.246:9000";
        return "http://localhost:9000";
    }
    return "";
}

function getColor(time) {
    var treshold = new Date().getTime() - (60 * 1000);
    if (time < treshold) {
        return "#c0c0c0";
    }
    return "#9999ff";
}

function getNetAddr(addr, tokensCount) {
    tokensCount = tokensCount || 3;
    var tokens = addr.split(".");
    return tokens.slice(0, tokensCount).join(".");
}

function diferentNetwork(from, to, tokensCount) {
    var fromTokens = from.split("."), toTokens = to.split("."), i;
    for (i = 0; i < tokensCount; i++) {
        if (fromTokens[i] !== toTokens[i]) return 1;
    }
    return 0;
}

function updateData(nodesCallback, edgesCallback) {
    
    $.getJSON(authority() + "/nodes", function(nodes) {
        
        nodesCallback(nodes.map(function(item){
            return {
                id: item.addr,
                label: item.host                    
            }
        }));

    });
        
    $.getJSON(authority() + "/edges", function(edges) {
        var start = new Date().getTime();
        var max = edges.reduce(function(acc, item) { return item.last > acc ? item.last : acc }, 0);
        var edgArr =edges.map( function(item) {
            var idarr = [item.source, item.dest ].sort();
            return {
                id: idarr[0] + "_" + idarr[1], 
                from: idarr[0],
                to: idarr[1],
                length: 100 + Math.floor((item.last / max) * ( 100 - 30 ) ) + diferentNetwork(item.source, item.dest, 3) * 50,
                //hidden: true,
                color: {
                    color: "rgba(100, 100, 100, 0.1)",
                    hover: "rgba(100, 100, 255, 0.5)",
                    highlight: "#6699ff"
                }
            }
        } );
        edgesCallback(edgArr);
        console.log(new Date().getTime() - start);
    }).fail(function( jqxhr, textStatus, error ) {
        var err = textStatus + ", " + error;
        console.log( "Request Failed: " + err );
    });
    
};

function updateTelemetry(cpuChart, memChart, addr) {
    if (!addr) return;
    
    var query = encodeURI(JSON.stringify({ addr: addr }));
    var sort = encodeURI(JSON.stringify({ timestamp: -1 }));
    $.getJSON(authority() + "/db/telemetry?q=" + query + "&sort=" + sort + "&limit=1", function(data) {
        if (!!data && data.length > 0) {
            renderTelemetry(cpuChart, memChart, data);
        }
    });
}

function renderTelemetry(cpuChart, memChart, telemetry) {
    var rows = telemetry.map(function(item) {
        return { cpu: Math.floor(item.cpu * 100), mem: Math.floor(item.memory) }  
    });
    var cpuMedian = rows.reduce(function(a,b) { return a + b.cpu; }, 0 ) / rows.length;
    cpuChart.segments[0].value = cpuMedian;
    cpuChart.segments[1].value = 100 - cpuMedian;
    cpuChart.update();
    $("#cpu_value").text("CPU " + cpuMedian + "%");
    
    var memMedian = rows.reduce(function(a,b) { return a + b.mem; }, 0 ) / rows.length;
    memChart.segments[0].value = memMedian;
    memChart.segments[1].value = 100 - memMedian;
    memChart.update();
    $("#mem_value").text("RAM " + memMedian + "%");
}

function TelemetryVizualizer(host) {
    $('.telemetry-history').show("fast");
    $('#telemetry-title').html(host);
    var telemetryContainer = $('#latency_viz')[0];
    var telemetryDataset = new vis.DataSet();
    var groups = new vis.DataSet();
    groups.add({
        id: 1,
        content: "CPU",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    groups.add({
        id: 2,
        content: "Memory",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    var options = {
        height: "25vh", 
        width: "100%", 
        interpolation: false,
        drawPoints: false,
        legend: true,
        /*dataAxis : {
            showMinorLabels: false
        }*/
    };
    var graph2d = new vis.Graph2d(telemetryContainer, telemetryDataset, groups, options);
    var query = { 
        query : { 
            bool: { 
                must: [
                    {
                        term: { 
                            addr: host 
                        }
                    }
                ]
            }
        }
    };
    var sort = encodeURI('timestamp:DESC');
    var isRunning = true;
    var lastQueryTime = new Date().getTime() - (5 * 60 * 1000);
    
    function plot() {
        if (!isRunning) {
            return;
        }
        
        var fromTime = lastQueryTime;
        var rangeQuery = { range: { timestamp : { gt: fromTime, lte: new Date().getTime() } }};
        query.query.bool.must[1] = rangeQuery;
        lastQueryTime = new Date().getTime();
        var q = authority() + "/db/telemetry?q=" + encodeURI(JSON.stringify(query)) + "&sort=" + sort + "&fields=add:cpu:memory:timestamp&limit=100";
        $.getJSON(q, function(data) {
            if (!!data) {
                var hits = data.hits.hits;
                var ltn = hits.reverse().reduce(function(acc, item) {
                    acc.push({y: Math.floor(item.fields.cpu[0] * 100), x: item.fields.timestamp[0], group: 1 });
                    acc.push({y: Math.floor(item.fields.memory[0]), x: item.fields.timestamp[0], group: 2 });
                    return acc;
                }, [] );
                telemetryDataset.update(ltn);
                graph2d.fit();
                
            }
        });
      
        setTimeout( plot, 5000 );
        
    }
    
    plot();
    
    this.stop = function() {
        isRunning = false;
        graph2d.destroy();
        $('.telemetry-history').hide();
    }
    
}

var glSelectedNode = undefined

function addItem(data, dataSet) {
    dataSet.update(data);
}

function LatencyVizualizer(from, to) {
    $('.telemetry-history').show("fast");
    $('#telemetry-title').html(from + " &rarr; " + to);
    var latencyContainer = $('#latency_viz')[0];
    var latencyDataset = new vis.DataSet();
    var groups = new vis.DataSet();
    groups.add({
        id: 1,
        content: "Direct",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    groups.add({
        id: 2,
        content: "Reverse",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    var options = {
        height: "25vh", 
        width: "100%", 
        interpolation: false,
        drawPoints: false,
        legend: true,
        dataAxis : {
            showMinorLabels: false
        }
    };
    var graph2d = new vis.Graph2d(latencyContainer, latencyDataset, groups, options);
    //var query = {query: { bool: { should: [  {match: {source: from}}, {match: {dest: to}} ] }}};
     var query = { 
        query : { 
            bool: { 
                must: [
                    {term: {source: from}}, 
                    {term: {dest: to}}
                ]
            }
        }
    };
    //var reverseQuery = {query: { bool: { should: [  {match: {source: to}}, {match: {dest: from}} ] }}};
    var reverseQuery = { 
        query : { 
            bool: { 
                must: [
                    {term: {source: to}}, 
                    {term: {dest: from}}
                ]
            }
        }
    };
    var sort = encodeURI('time:DESC');
    var isRunning = true;
    var lastQueryTime = new Date().getTime() - (5 * 60 * 1000);
    
    function plot() {
        if (!isRunning) {
            return;
        }
        
        var now = new Date().getTime();
        var fromTime = lastQueryTime;
        var rangeQuery = { range: { time : { gt: fromTime, lte: now } }};
        query.query.bool.must[2] = rangeQuery;
        reverseQuery.query.bool.must[2] = rangeQuery;
        lastQueryTime = now;
        
        var q = authority() + "/db/latency?q=" + encodeURI(JSON.stringify(query)) + "&sort=" + sort + "&fields=time:pingTotal&limit=100";
        var q2 = authority() + "/db/latency?q=" + encodeURI(JSON.stringify(reverseQuery)) + "&sort=" + sort + "&fields=time:pingTotal&limit=100";
        var plainReq = $.getJSON(q);
        var reverseReq = $.getJSON(q2);
        $.when(plainReq, reverseReq).done(function(dataPlain, dataReverse) {
            var ltn1 = dataPlain[0].hits.hits.map(function(item) { 
               return {
                   x: item.fields.time[0],
                   y: item.fields.pingTotal[0],
                   group: 1
               }
            }).reverse();
            var ltn2 = dataReverse[0].hits.hits.map(function(item) { 
               return {
                   x: item.fields.time[0],
                   y: item.fields.pingTotal[0],
                   group: 2
               }
            }).reverse();
            var arr = ltn1.concat(ltn2);
            
            latencyDataset.update(arr);
            graph2d.fit();
            
        });
    
        setTimeout( plot, 10000 );
        
    }
    
    plot();
    
    this.stop = function() {
        isRunning = false;
        graph2d.destroy();
        $('.telemetry-history').hide();
    }
    
}

var vizualizer;

$(function() {
    
    var nodes = new vis.DataSet(),
        edges = new vis.DataSet();
            
    var container = $('#graph')[0];
    var data = {
        nodes: nodes,
        edges: edges
    };
    var options = {
        nodes: {
            shape: "dot",
            size: 10,
            font: {
                size: 10
            }
        },
        edges: {
            smooth: false,
        },
        physics: {
            barnesHut: {
                "gravitationalConstant": -1000,
                "springLength": 10,
                "springConstant": 0.1,
                "damping": 1
            },
            "maxVelocity": 150,
            "minVelocity": 0.75,
            stabilization: {
                iterations: 20
            }
        },
        interaction: {
          navigationButtons: true,
          keyboard: true
        }
    };
    
     var animationOptions = {
        offset: {x: 0, y: 0},
        duration: 1000,
        easingFunction: "easeInOutQuad"  
    }
    
    var network = new vis.Network(container, data, options);
    
    function showLatency(from, to) {
        if (!!vizualizer) {
            vizualizer.stop();
            vizualizer = undefined;
        }
        vizualizer = new LatencyVizualizer(from, to);
    }
    
    function showTelemetry(host) {
        if (!!vizualizer) {
            vizualizer.stop();
            vizualizer = undefined;
        }
        vizualizer = new TelemetryVizualizer(host);
    }
    
    network.on("click", function(e) {
        if ( !!e.edges && e.edges.length > 0 && !e.nodes.length ) {
            var edgeId = e.edges[0];
            var edge = edges.get(edgeId);
            showLatency(edge.from, edge.to);
            $('.from').val(edge.from);
            $('.to').val(edge.to);
        } else if ( !!e.nodes && e.nodes.length > 0 ) {
            var node = e.nodes[0];
            showTelemetry(node);
            $('.from').val(node);
            $('.to').val("");
        } else {
            $('.from').val("");
            $('.to').val("");
        }
    });
    
    function selectFromList(item) {
        network.selectNodes([item]);
        var from = $('.from').val();
        var to = $('.to').val();
        
        if (!!from && !!to) {
            var idarr = [from, to].sort();
            network.selectEdges([idarr[0] + "_" + idarr[1]]);
            showLatency(from, to)
        }
    }
    
    
    
    $('.telemetry-history').hide();
    $(".node-list").hide();
    
    $(".node-list-btn").on('click', function(){
        var nodeLst = $(".node-list");
        if ( nodeLst.is(":visible") ) 
            nodeLst.hide();
        else
            nodeLst.show();
    });
    var firstTimeFit = true;
    function updateCallback() {
        var oldNodes = "";
        updateData(
            function(data) { 
                if ( oldNodes !== data.toString() ) {
                    addItem(data, nodes); 
                    $(".typehead").typeahead({source: data.map(function(item){ return item.id; }), afterSelect: selectFromList});
                    oldNodes = data.toString();
                    $(".node-list").empty();
                    data.forEach(function(item) {
                        $(".node-list").append("<div class='node-list-item'><span>" + item.id + "</span></div>");    
                    });
                    $('.node-list-item').on('click', function(element) {
                        var value = $(this).text();
                        network.selectNodes([value]);
                        $('.from').val(value);
                        $('.to').val("");
                        $('.node-list').hide();
                        network.focus(value, {animation: animationOptions});
                    });
                }
                if (firstTimeFit) {
                    network.fit({animation: animationOptions}); 
                    firstTimeFit = false;
                }
            }, 
            function(data) { 
                addItem(data, edges); 
                //network.fit({animation: animationOptions}); 
                setTimeout(updateCallback, 10000);
            }
        );
    };
    
    updateCallback();
    //setInterval(function() { 
    //    updateData(function(data) { addItem(data, nodes); }, function(data) { addItem(data, edges); /*network.fit();*/ //});
    //}, 10000);
    
    ///setInterval(updateCallback, 10000);
    
});