// ========== Global State ==========
const appState = {
    sessionId: null,
    sessionStatus: 'NOT_STARTED',
    language: 'en',
    currentRound: 1,
    maxRounds: 5,
    timeRemaining: 180,
    timerInterval: null,
    chatMessages: [], // Array of {id, speaker, role, content, timestamp, round, messageType}
    streamingState: {
        active: false,
        speaker: null,
        content: '',
        eventType: ''
    },
    scores: { affirmativeTotal: 0, negativeTotal: 0 },
    roundScores: [],
    selectedTopic: null,
    affirmativeConfig: { personality: 'Analytical', expertiseLevel: 'Expert' },
    negativeConfig: { personality: 'Passionate', expertiseLevel: 'Expert' },
    autoPlaySpeed: 'NORMAL',
    isPaused: false,
    translations: {},
    streamingSSE: null,
    // Audio playback state
    audioPlayer: {
        currentAudio: null,
        isPlaying: false,
        currentMessageId: null,
        playbackPosition: 0
    }
};

// ========== I18n Translation System ==========
let i18nEn = {};
let i18nZh = {};

async function loadTranslations() {
    try {
        const [enResponse, zhResponse] = await Promise.all([
            fetch('/i18n.en.json'),
            fetch('/i18n.zh.json')
        ]);
        i18nEn = await enResponse.json();
        i18nZh = await zhResponse.json();
        
        // Detect browser language
        const browserLang = navigator.language.toLowerCase();
        if (browserLang.startsWith('zh')) {
            appState.language = 'zh';
        }
        
        // Load from localStorage if available
        const savedLang = localStorage.getItem('debateLang');
        if (savedLang) {
            appState.language = savedLang;
        }
        
        applyTranslations();
    } catch (error) {
        console.error('Failed to load translations:', error);
    }
}

function t(key) {
    const keys = key.split('.');
    let value = appState.language === 'zh' ? i18nZh : i18nEn;
    
    for (const k of keys) {
        value = value[k];
        if (!value) return key;
    }
    
    return value;
}

function applyTranslations() {
    // Header
    document.getElementById('header-title').textContent = '⚔️ ' + t('header.title') + ' ⚔️';
    document.getElementById('header-subtitle').textContent = t('header.subtitle');
    document.getElementById('lang-label').textContent = appState.language === 'zh' ? '中文' : 'EN';
    
    // Topic selector
    document.getElementById('topic-label').textContent = t('topics.currentTopic') + ':';
    document.getElementById('btn-select-topic').textContent = t('buttons.selectTopic');
    
    // Flow panel
    document.getElementById('flow-title').textContent = t('flow.title');
    document.getElementById('label-current-round').textContent = t('flow.currentRound') + ':';
    document.getElementById('label-time').textContent = t('flow.timeRemaining') + ':';
    
    document.getElementById('flow-r1-label').textContent = t('flow.rounds.round1');
    document.getElementById('flow-r2-label').textContent = t('flow.rounds.round2');
    document.getElementById('flow-r3-label').textContent = t('flow.rounds.round3');
    document.getElementById('flow-r4-label').textContent = t('flow.rounds.round4');
    document.getElementById('flow-r5-label').textContent = t('flow.rounds.round5');
    document.getElementById('flow-j-label').textContent = t('flow.rounds.judging');
    
    document.getElementById('rules-title').textContent = t('flow.rules');
    document.getElementById('rule-max-length').textContent = '• ' + t('flow.maxLength');
    document.getElementById('rule-time-limit').textContent = '• ' + t('flow.timeLimit');
    document.getElementById('rule-scoring').textContent = '• ' + t('flow.scoringCriteria');
    document.getElementById('rule-prohibited').textContent = '• ' + t('flow.prohibited');
    
    // Score board
    document.getElementById('score-title').textContent = t('score.title');
    document.getElementById('score-affirmative-label').textContent = t('debate.affirmative');
    document.getElementById('score-negative-label').textContent = t('debate.negative');
    
    // Chat area
    document.getElementById('chat-empty-text').textContent = t('chat.emptyState');
    
    // Playback controls
    document.getElementById('playback-title').textContent = t('config.aiConfiguration');
    document.getElementById('btn-start-debate').textContent = '▶ ' + t('buttons.startDebate');
    document.getElementById('btn-pause-debate').textContent = '⏸ ' + t('buttons.pauseDebate');
    document.getElementById('btn-resume-debate').textContent = '▶ ' + t('buttons.resumeDebate');
    document.getElementById('btn-skip-debate').textContent = '⏭ ' + t('buttons.skipToEnd');
    
    // Modal
    document.getElementById('modal-topics-title').textContent = t('topics.title');
    
    // AI Config Modal
    document.getElementById('modal-config-title').textContent = t('config.aiConfiguration');
    document.getElementById('config-affirmative-title').textContent = t('config.affirmativeSide');
    document.getElementById('config-negative-title').textContent = t('config.negativeSide');
    document.getElementById('label-affirmative-personality').textContent = t('config.personality');
    document.getElementById('label-affirmative-expertise').textContent = t('config.expertiseLevel');
    document.getElementById('label-negative-personality').textContent = t('config.personality');
    document.getElementById('label-negative-expertise').textContent = t('config.expertiseLevel');
    document.getElementById('label-autoplay-speed').textContent = t('config.autoPlaySpeed');
    document.getElementById('speed-fast-label').textContent = t('speed.fast');
    document.getElementById('speed-normal-label').textContent = t('speed.normal');
    document.getElementById('speed-slow-label').textContent = t('speed.slow');
    document.getElementById('btn-save-config').textContent = t('buttons.startDebate');
    document.getElementById('btn-cancel-config').textContent = 'Cancel';
    
    // Update observer badge
    document.getElementById('observer-badge').textContent = t('modes.observer');
    
    // Footer
    document.getElementById('footer-text').textContent = t('footer.text');
    
    // Buttons
    document.getElementById('btn-new-debate').textContent = t('buttons.startNewDebate');
}

function toggleLanguage() {
    appState.language = appState.language === 'en' ? 'zh' : 'en';
    localStorage.setItem('debateLang', appState.language);
    applyTranslations();
}

// ========== Topic Management ==========
async function loadTopics() {
    try {
        const response = await fetch('/api/topics');
        const topics = await response.json();
        
        const topicsList = document.getElementById('topics-list');
        if (topics.length === 0) {
            topicsList.innerHTML = `<div class="col-span-3 text-center text-gray-500">${t('topics.noTopics')}</div>`;
            return;
        }
        
        topicsList.innerHTML = topics.map(topic => `
            <div class="p-4 bg-wasteland-darker rounded border border-wasteland-gold hover:border-wasteland-gold-light transition cursor-pointer" 
                 onclick="selectTopic(${topic.topicId}, '${escapeHtml(topic.title)}')">
                <h3 class="font-bold text-wasteland-gold-light mb-2 text-sm">${escapeHtml(topic.title)}</h3>
                <p class="text-xs text-gray-400 mb-2">${escapeHtml(topic.description || 'No description')}</p>
                <div class="flex justify-between items-center text-xs text-gray-500">
                    <span class="bg-wasteland-blue px-2 py-1 rounded">${escapeHtml(topic.category || 'General')}</span>
                </div>
            </div>
        `).join('');
    } catch (error) {
        console.error('Error loading topics:', error);
        document.getElementById('topics-list').innerHTML = 
            `<div class="col-span-3 text-center text-red-400">${t('messages.errorLoadingTopics')}</div>`;
    }
}

function selectTopic(topicId, topicTitle) {
    appState.selectedTopic = { id: topicId, title: topicTitle };
    
    // Update UI
    document.getElementById('selected-topic-display').textContent = topicTitle;
    document.getElementById('btn-select-topic').textContent = t('buttons.changeTopic');
    
    // Close topic modal
    closeTopicModal();
    
    // Open AI configuration modal
    openAIConfigModal();
}

// ========== AI Configuration Modal ==========
function openAIConfigModal() {
    document.getElementById('ai-config-modal').classList.remove('hidden');
    document.getElementById('ai-config-modal').classList.add('flex');
}

function closeAIConfigModal() {
    document.getElementById('ai-config-modal').classList.add('hidden');
    document.getElementById('ai-config-modal').classList.remove('flex');
}

function saveAIConfiguration() {
    // Get AI configurations
    appState.affirmativeConfig = {
        personality: document.getElementById('affirmative-personality').value,
        expertiseLevel: document.getElementById('affirmative-expertise').value
    };
    
    appState.negativeConfig = {
        personality: document.getElementById('negative-personality').value,
        expertiseLevel: document.getElementById('negative-expertise').value
    };
    
    // Get auto-play speed
    const speedRadios = document.getElementsByName('autoplay-speed');
    for (const radio of speedRadios) {
        if (radio.checked) {
            appState.autoPlaySpeed = radio.value;
            break;
        }
    }
    
    // Close modal
    closeAIConfigModal();
    
    // Initialize session
    initializeSession();
}

// ========== Session Management ==========
async function initializeSession() {
    try {
        const response = await fetch('/api/debates/init', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                topicId: appState.selectedTopic.id,
                userId: 1,
                aiConfigs: {
                    affirmative: appState.affirmativeConfig,
                    negative: appState.negativeConfig
                },
                autoPlaySpeed: appState.autoPlaySpeed
            })
        });
        
        const data = await response.json();
        appState.sessionId = data.sessionId;
        
        // Reset state
        appState.sessionStatus = 'INITIALIZED';
        appState.chatMessages = [];
        appState.currentRound = 1;
        appState.scores = { affirmativeTotal: 0, negativeTotal: 0 };
        appState.isPaused = false;
        
        updateSessionStatusUI();
        updateUI();
        
        // Show start debate button
        document.getElementById('btn-start-debate').style.display = 'inline-block';
        document.getElementById('playback-status-text').textContent = 'Ready to start';
        
        showToast('Debate initialized! Click "Start Debate" to begin.', 'success');
    } catch (error) {
        console.error('Error initializing session:', error);
        showToast(t('messages.failedToStart'), 'error');
    }
}

// ========== Automated Debate Controls ==========
async function startAutomatedDebate() {
    if (!appState.sessionId) {
        showToast(t('messages.pleaseSelectTopic'), 'warning');
        return;
    }
    
    try {
        // Hide start button, show pause and skip buttons
        document.getElementById('btn-start-debate').style.display = 'none';
        document.getElementById('btn-pause-debate').style.display = 'inline-block';
        document.getElementById('btn-pause-debate').disabled = false;
        document.getElementById('btn-skip-debate').style.display = 'inline-block';
        document.getElementById('btn-skip-debate').disabled = false;
        document.getElementById('playback-status-text').textContent = t('states.inProgress');
        
        appState.sessionStatus = 'IN_PROGRESS';
        updateSessionStatusUI();
        
        // Start timer
        startTimer();
        
        // Start the session and connect to streaming
        await fetch(`/api/debates/${appState.sessionId}/start`, { method: 'POST' });
        
        // Connect to debate stream
        connectDebateStream();
        
    } catch (error) {
        console.error('Error starting automated debate:', error);
        showToast('Failed to start debate: ' + error.message, 'error');
    }
}

async function pauseDebate() {
    if (!appState.sessionId) return;
    
    try {
        await fetch(`/api/debates/${appState.sessionId}/pause`, { method: 'POST' });
        appState.isPaused = true;
        appState.sessionStatus = 'PAUSED';
        
        // Update UI
        document.getElementById('btn-pause-debate').disabled = true;
        document.getElementById('btn-resume-debate').style.display = 'inline-block';
        document.getElementById('btn-resume-debate').disabled = false;
        document.getElementById('playback-status-text').textContent = t('states.paused');
        
        updateSessionStatusUI();
        stopTimer();
        
        // Close SSE connection
        closeStreamingSSE();
        
        showToast('Debate paused', 'info');
    } catch (error) {
        console.error('Error pausing debate:', error);
        showToast('Failed to pause debate', 'error');
    }
}

async function resumeDebate() {
    if (!appState.sessionId) return;
    
    try {
        await fetch(`/api/debates/${appState.sessionId}/resume`, { method: 'POST' });
        appState.isPaused = false;
        appState.sessionStatus = 'IN_PROGRESS';
        
        // Update UI
        document.getElementById('btn-resume-debate').disabled = true;
        document.getElementById('btn-pause-debate').disabled = false;
        document.getElementById('playback-status-text').textContent = t('states.inProgress');
        
        updateSessionStatusUI();
        startTimer();
        
        // Reconnect to stream
        connectDebateStream();
        
        showToast('Debate resumed', 'info');
    } catch (error) {
        console.error('Error resuming debate:', error);
        showToast('Failed to resume debate', 'error');
    }
}

async function skipToEnd() {
    if (!appState.sessionId) return;
    
    if (!confirm('Skip to final judging? The debate will end immediately.')) {
        return;
    }
    
    try {
        await fetch(`/api/debates/${appState.sessionId}/skip-to-end`, { method: 'POST' });
        
        // Close current stream
        closeStreamingSSE();
        
        // Reconnect to get judging sequence
        connectDebateStream();
        
        showToast('Skipping to final judging...', 'info');
    } catch (error) {
        console.error('Error skipping to end:', error);
        showToast('Failed to skip to end', 'error');
    }
}

// ========== SSE Streaming ==========
function connectDebateStream() {
    if (appState.streamingSSE) {
        appState.streamingSSE.close();
    }
    
    const url = `/api/debates/${appState.sessionId}/stream-debate?language=${appState.language}`;
    
    fetch(url, {
        method: 'GET',
        headers: { 'Accept': 'text/event-stream' }
    }).then(response => {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        
        function processStream() {
            reader.read().then(({ done, value }) => {
                if (done) {
                    closeStreamingSSE();
                    return;
                }
                
                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop(); // Keep incomplete line in buffer
                
                for (const line of lines) {
                    if (line.startsWith('event:')) {
                        const eventType = line.substring(6).trim();
                        handleSSEEvent(eventType);
                    } else if (line.startsWith('data:')) {
                        const data = line.substring(5).trim();
                        if (data) {
                            try {
                                const eventData = JSON.parse(data);
                                handleSSEData(eventData);
                            } catch (e) {
                                console.error('Error parsing SSE data:', e);
                            }
                        }
                    }
                }
                
                processStream();
            }).catch(error => {
                console.error('SSE stream error:', error);
                closeStreamingSSE();
            });
        }
        
        processStream();
    }).catch(error => {
        console.error('Error connecting to debate stream:', error);
        showToast('Failed to connect to debate stream: ' + error.message, 'error');
    });
}

let currentEventType = '';

function handleSSEEvent(eventType) {
    currentEventType = eventType;
}

function handleSSEData(data) {
    console.log('SSE Event:', currentEventType, data);
    
    switch (currentEventType) {
        case 'debate_start':
            handleDebateStart(data);
            break;
        case 'organizer_rules':
            handleOrganizerRules(data);
            break;
        case 'moderator_introduction':
            handleModeratorIntroduction(data);
            break;
        case 'round_start':
            handleRoundStart(data);
            break;
        case 'user_argument':
            handleUserArgumentEvent(data);
            break;
        case 'moderator_summary':
            handleModeratorEvent(data, 'SUMMARY');
            break;
        case 'moderator_evaluation':
            handleModeratorEvent(data, 'EVALUATION');
            break;
        case 'moderator_announcement':
            handleModeratorEvent(data, 'ANNOUNCEMENT');
            break;
        case 'ai_argument':
            handleAIArgumentEvent(data);
            break;
        case 'round_scores_update':
            handleRoundScoresUpdate(data);
            break;
        case 'cumulative_scores_update':
            handleCumulativeScoresUpdate(data);
            break;
        case 'scores_update':
            handleScoresUpdate(data);
            break;
        case 'judging_start':
            handleJudgingStart(data);
            break;
        case 'judge_feedback':
            handleJudgeFeedback(data);
            break;
        case 'winner_announcement':
            handleWinnerAnnouncement(data);
            break;
        case 'stream_complete':
        case 'debate_complete':
            handleStreamComplete(data);
            break;
        case 'debate_paused':
            handleDebatePaused(data);
            break;
        case 'error':
            handleStreamError(data);
            break;
    }
}

function handleDebateStart(data) {
    console.log('Debate started:', data);
    addChatMessage({
        id: 'debate-start-' + Date.now(),
        speaker: 'MODERATOR',
        role: 'MODERATOR',
        content: `Debate on "${data.topic}" has begun!`,
        timestamp: data.timestamp,
        round: 0,
        messageType: 'ANNOUNCEMENT'
    });
}

function handleOrganizerRules(data) {
    if (data.complete) {
        // Get accumulated content from streaming state
        const finalContent = appState.streamingState.active && appState.streamingState.speaker === 'ORGANIZER' 
            ? appState.streamingState.content 
            : data.chunk;
        
        hideStreamingPreview();
        
        // Only add message if there's content
        if (finalContent && finalContent.trim() !== '') {
            addChatMessage({
                id: 'organizer-rules-' + Date.now(),
                speaker: 'ORGANIZER',
                role: 'ORGANIZER',
                content: finalContent,
                timestamp: data.timestamp,
                round: 0,
                messageType: 'ANNOUNCEMENT'
            });
        }
    } else {
        showStreamingPreview('ORGANIZER', data.chunk);
    }
}

function handleModeratorIntroduction(data) {
    if (data.complete) {
        // Get accumulated content from streaming state
        const finalContent = appState.streamingState.active && appState.streamingState.speaker === 'MODERATOR' 
            ? appState.streamingState.content 
            : data.chunk;
        
        hideStreamingPreview();
        
        // Only add message if there's content
        if (finalContent && finalContent.trim() !== '') {
            addChatMessage({
                id: 'moderator-intro-' + Date.now(),
                speaker: 'MODERATOR',
                role: 'MODERATOR',
                content: finalContent,
                timestamp: data.timestamp,
                round: 0,
                messageType: 'ANNOUNCEMENT'
            });
        }
    } else {
        showStreamingPreview('MODERATOR', data.chunk);
    }
}

function handleRoundStart(data) {
    appState.currentRound = data.round;
    updateUI();
    addChatMessage({
        id: 'round-start-' + Date.now(),
        speaker: 'MODERATOR',
        role: 'MODERATOR',
        content: `Round ${data.round} begins!`,
        timestamp: data.timestamp,
        round: data.round,
        messageType: 'ANNOUNCEMENT'
    });
}

function handleUserArgumentEvent(data) {
    if (data.complete) {
        addChatMessage({
            id: 'user-' + data.argumentId,
            speaker: appState.userSide,
            role: data.role,
            content: data.content,
            timestamp: data.timestamp,
            round: data.round,
            messageType: 'ARGUMENT'
        });
    }
}

function handleModeratorEvent(data, messageType) {
    if (data.complete) {
        // Get accumulated content from streaming state
        const finalContent = appState.streamingState.active && appState.streamingState.speaker === 'MODERATOR' 
            ? appState.streamingState.content 
            : data.chunk;
        
        // Hide streaming preview and add to chat
        hideStreamingPreview();
        
        // Only add message if there's content
        if (finalContent && finalContent.trim() !== '') {
            addChatMessage({
                id: 'moderator-' + Date.now(),
                speaker: 'MODERATOR',
                role: 'MODERATOR',
                content: finalContent,
                timestamp: data.timestamp,
                round: appState.currentRound,
                messageType: messageType
            });
        }
    } else {
        // Show in streaming preview
        showStreamingPreview('MODERATOR', data.chunk);
    }
}

function handleAIArgumentEvent(data) {
    // Determine side from data
    const side = data.side || (appState.userSide === 'AFFIRMATIVE' ? 'NEGATIVE' : 'AFFIRMATIVE');
    
    if (data.complete) {
        // Get accumulated content from streaming state
        const finalContent = appState.streamingState.active && appState.streamingState.speaker === side 
            ? appState.streamingState.content 
            : data.chunk;
        
        // Hide streaming preview and add to chat
        hideStreamingPreview();
        
        // Only add message if there's content
        if (finalContent && finalContent.trim() !== '') {
            addChatMessage({
                id: 'ai-' + (data.argumentId || Date.now()),
                speaker: side,
                role: data.role || side,
                content: finalContent,
                timestamp: data.timestamp,
                round: data.round || appState.currentRound,
                messageType: 'ARGUMENT'
            });
        }
    } else {
        // Show in streaming preview
        showStreamingPreview(side, data.chunk);
    }
}

// ========== SCORING EVENT HANDLERS ==========

function handleRoundScoresUpdate(data) {
    console.log('Round scores updated:', data);
    
    // Display round score announcement
    const roundScoreMessage = appState.language === 'zh' 
        ? `第${data.round}回合评分 - 正方: ${data.affirmativeScore.toFixed(1)}, 反方: ${data.negativeScore.toFixed(1)}`
        : `Round ${data.round} Scores - Affirmative: ${data.affirmativeScore.toFixed(1)}, Negative: ${data.negativeScore.toFixed(1)}`;
    
    addChatMessage({  
        id: 'round-score-' + data.round + '-' + Date.now(),
        speaker: 'MODERATOR',
        role: 'MODERATOR',
        content: roundScoreMessage,
        timestamp: data.timestamp,
        round: data.round,
        messageType: 'ANNOUNCEMENT'
    });
    
    // Store round scores for potential detailed view
    if (!appState.roundScores) {
        appState.roundScores = [];
    }
    appState.roundScores.push({
        round: data.round,
        affirmative: data.affirmativeScore,
        negative: data.negativeScore
    });
}

function handleCumulativeScoresUpdate(data) {
    console.log('Cumulative scores updated:', data);
    
    // Update scoreboard display
    appState.scores.affirmativeTotal = data.affirmativeTotal || 0;
    appState.scores.negativeTotal = data.negativeTotal || 0;
    updateScores();
}

function handleScoresUpdate(data) {
    appState.scores.affirmativeTotal = data.affirmativeScore || data.affirmativeTotal || 0;
    appState.scores.negativeTotal = data.negativeScore || data.negativeTotal || 0;
    updateScores();
}

function handleJudgingStart(data) {
    console.log('Judging phase started:', data);
    document.getElementById('flow-judging').classList.add('active');
    
    // Disable pause button during judging
    document.getElementById('btn-pause-debate').disabled = true;
    document.getElementById('btn-skip-debate').disabled = true;
    
    addChatMessage({
        id: 'judging-start-' + Date.now(),
        speaker: 'MODERATOR',
        role: 'MODERATOR',
        content: 'The debate has concluded. Judges will now provide their feedback.',
        timestamp: data.timestamp,
        round: 6,
        messageType: 'ANNOUNCEMENT'
    });
}

function handleJudgeFeedback(data) {
    const speaker = 'JUDGE ' + (data.judgeNumber || '');
    
    if (data.complete) {
        // Get accumulated content from streaming state
        const finalContent = appState.streamingState.active && appState.streamingState.speaker === speaker 
            ? appState.streamingState.content 
            : data.chunk;
        
        hideStreamingPreview();
        
        // Only add message if there's content
        if (finalContent && finalContent.trim() !== '') {
            addChatMessage({
                id: 'judge-feedback-' + Date.now(),
                speaker: 'JUDGE',
                role: 'JUDGE',
                content: finalContent,
                timestamp: data.timestamp,
                round: 6,
                messageType: 'EVALUATION'
            });
        }
    } else {
        showStreamingPreview(speaker, data.chunk);
    }
}

function handleWinnerAnnouncement(data) {
    if (data.complete) {
        // Get accumulated content from streaming state
        const finalContent = appState.streamingState.active && appState.streamingState.speaker === 'MODERATOR' 
            ? appState.streamingState.content 
            : data.chunk;
        
        hideStreamingPreview();
        
        // Only add message if there's content
        if (finalContent && finalContent.trim() !== '') {
            addChatMessage({
                id: 'winner-' + Date.now(),
                speaker: 'MODERATOR',
                role: 'MODERATOR',
                content: finalContent,
                timestamp: data.timestamp,
                round: 6,
                messageType: 'ANNOUNCEMENT'
            });
        }
    } else {
        showStreamingPreview('MODERATOR', data.chunk);
    }
}

function handleDebatePaused(data) {
    appState.isPaused = true;
    appState.sessionStatus = 'PAUSED';
    updateSessionStatusUI();
    
    document.getElementById('btn-pause-debate').disabled = true;
    document.getElementById('btn-resume-debate').style.display = 'inline-block';
    document.getElementById('btn-resume-debate').disabled = false;
    document.getElementById('playback-status-text').textContent = t('states.paused');
    
    showToast('Debate paused at round ' + data.round, 'info');
}

function handleStreamComplete(data) {
    console.log('Stream complete:', data);
    closeStreamingSSE();
    
    // Update to completed state
    appState.sessionStatus = 'COMPLETED';
    updateSessionStatusUI();
    
    // Stop timer
    stopTimer();
    
    // Hide playback controls, show new debate button
    document.getElementById('btn-start-debate').style.display = 'none';
    document.getElementById('btn-pause-debate').style.display = 'none';
    document.getElementById('btn-resume-debate').style.display = 'none';
    document.getElementById('btn-skip-debate').style.display = 'none';
    document.getElementById('new-debate-section').classList.remove('hidden');
    document.getElementById('playback-status-text').textContent = 'Debate Complete';
}

function handleStreamError(data) {
    console.error('Stream error:', data);
    showToast(data.error || 'An error occurred during streaming', 'error');
    closeStreamingSSE();
    document.getElementById('btn-submit').disabled = false;
    document.getElementById('argument-input').disabled = false;
}

function closeStreamingSSE() {
    if (appState.streamingSSE) {
        appState.streamingSSE.close();
        appState.streamingSSE = null;
    }
    hideStreamingPreview();
}

// ========== Chat UI Functions ==========
function addChatMessage(message) {
    console.log('Adding chat message:', {
        speaker: message.speaker,
        contentLength: message.content ? message.content.length : 0,
        contentPreview: message.content ? message.content.substring(0, 50) + '...' : 'EMPTY',
        round: message.round
    });
    
    appState.chatMessages.push(message);
    
    const chatContainer = document.getElementById('chat-messages');
    const emptyState = document.getElementById('chat-empty-state');
    
    // Hide empty state
    if (emptyState) {
        emptyState.style.display = 'none';
    }
    
    // Create message element
    const messageEl = createMessageElement(message);
    chatContainer.appendChild(messageEl);
    
    // Scroll to bottom
    scrollToBottom();
}

function createMessageElement(message) {
    const div = document.createElement('div');
    
    // Determine alignment
    const isLeft = message.speaker === 'AFFIRMATIVE' || message.speaker === 'MODERATOR';
    div.className = `chat-message ${isLeft ? 'chat-message-left' : 'chat-message-right'}`;
    
    // Create message bubble
    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';
    
    // Add speaker-specific styling
    if (message.speaker === 'MODERATOR') {
        bubble.classList.add('message-bubble-moderator');
    } else if (message.speaker === 'AFFIRMATIVE') {
        bubble.classList.add('message-bubble-affirmative');
    } else if (message.speaker === 'NEGATIVE') {
        bubble.classList.add('message-bubble-negative');
    }
    
    // Create header
    const header = document.createElement('div');
    header.className = 'flex items-center gap-2 mb-1';
    
    const indicator = document.createElement('div');
    indicator.className = 'w-2 h-2 rounded-full';
    if (message.speaker === 'MODERATOR') {
        indicator.classList.add('bg-amber-500');
    } else if (message.speaker === 'AFFIRMATIVE') {
        indicator.classList.add('bg-blue-500');
    } else {
        indicator.classList.add('bg-red-500');
    }
    
    const speakerLabel = document.createElement('span');
    speakerLabel.className = 'text-xs font-bold';
    speakerLabel.textContent = getSpeakerLabel(message.speaker);
    
    const timestamp = document.createElement('span');
    timestamp.className = 'text-xs text-gray-500';
    timestamp.textContent = formatTimestamp(message.timestamp);
    
    // Create audio button
    const audioButton = document.createElement('button');
    audioButton.className = 'ml-auto text-lg hover:scale-110 transition-transform audio-idle';
    audioButton.setAttribute('data-audio-btn', message.id);
    audioButton.title = t('chat.readAloud');
    audioButton.onclick = (e) => {
        e.stopPropagation();
        toggleAudioPlayback(message);
    };
    
    const audioIcon = document.createElement('span');
    audioIcon.className = 'audio-icon';
    audioIcon.textContent = '🔊';
    audioButton.appendChild(audioIcon);
    
    header.appendChild(indicator);
    header.appendChild(speakerLabel);
    header.appendChild(timestamp);
    header.appendChild(audioButton);
    
    // Create content container
    const contentContainer = document.createElement('div');
    
    // Create content element
    const content = document.createElement('div');
    content.className = 'text-sm message-content';
    content.textContent = message.content;
    
    // Check if content exceeds max height (approximately 200px / 14px line height = ~14 lines)
    const isTruncated = message.content.length > 400; // Rough estimate
    if (isTruncated) {
        content.classList.add('truncated');
        
        // Create "View Full" button
        const viewFullBtn = document.createElement('span');
        viewFullBtn.className = 'view-full-btn';
        viewFullBtn.textContent = t('chat.viewFull') || 'View Full Content';
        viewFullBtn.onclick = () => showFullContentModal(message);
        
        contentContainer.appendChild(content);
        contentContainer.appendChild(viewFullBtn);
    } else {
        contentContainer.appendChild(content);
    }
    
    // Create footer (round info)
    const footer = document.createElement('div');
    footer.className = 'text-xs text-gray-500 mt-1';
    footer.textContent = `${t('debate.round')} ${message.round}`;
    
    bubble.appendChild(header);
    bubble.appendChild(contentContainer);
    bubble.appendChild(footer);
    
    div.appendChild(bubble);
    
    return div;
}

function getSpeakerLabel(speaker) {
    if (speaker === 'MODERATOR') {
        return t('chat.moderator');
    } else if (speaker === 'AFFIRMATIVE') {
        return t('chat.affirmative');
    } else if (speaker === 'NEGATIVE') {
        return t('chat.negative');
    }
    return speaker;
}

function formatTimestamp(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleTimeString(appState.language === 'zh' ? 'zh-CN' : 'en-US', {
        hour: '2-digit',
        minute: '2-digit'
    });
}

function showStreamingPreview(speaker, content) {
    const container = document.getElementById('streaming-preview-container');
    const speakerEl = document.getElementById('streaming-speaker');
    const contentEl = document.getElementById('streaming-content');
    
    container.classList.remove('hidden');
    speakerEl.textContent = getSpeakerLabel(speaker) + ' ' + t('chat.streaming');
    
    // Optimization #2: Append content instead of replacing
    if (appState.streamingState.active && appState.streamingState.speaker === speaker) {
        // Same speaker still streaming - append new content
        // Check if content is empty (completion signal) - don't append
        if (content && content.trim() !== '') {
            contentEl.textContent += content;
        }
    } else {
        // New speaker or first chunk - replace content
        contentEl.textContent = content;
    }
    
    appState.streamingState.active = true;
    appState.streamingState.speaker = speaker;
    appState.streamingState.content = contentEl.textContent;
    
    scrollToBottom();
}

function hideStreamingPreview() {
    const container = document.getElementById('streaming-preview-container');
    container.classList.add('hidden');
    
    appState.streamingState.active = false;
    appState.streamingState.speaker = null;
    appState.streamingState.content = '';
}

function scrollToBottom() {
    const chatContainer = document.getElementById('chat-messages');
    setTimeout(() => {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }, 100);
}

// ========== Full Content Modal ==========
function showFullContentModal(message) {
    console.log('Opening full content modal for message:', message);
    console.log('Content length:', message.content ? message.content.length : 0);
    console.log('Content preview:', message.content ? message.content.substring(0, 100) : 'EMPTY');
    
    const modal = document.getElementById('full-content-modal');
    const speakerEl = document.getElementById('full-content-speaker');
    const indicatorEl = document.getElementById('full-content-speaker-indicator');
    const timestampEl = document.getElementById('full-content-timestamp');
    const roundEl = document.getElementById('full-content-round');
    const textEl = document.getElementById('full-content-text');
    
    if (!modal || !textEl) {
        console.error('Modal elements not found!');
        return;
    }
    
    // Set speaker label
    speakerEl.textContent = getSpeakerLabel(message.speaker);
    
    // Set indicator color
    indicatorEl.className = 'w-3 h-3 rounded-full';
    if (message.speaker === 'MODERATOR') {
        indicatorEl.classList.add('bg-amber-500');
    } else if (message.speaker === 'AFFIRMATIVE') {
        indicatorEl.classList.add('bg-blue-500');
    } else if (message.speaker === 'ORGANIZER') {
        indicatorEl.classList.add('bg-purple-500');
    } else if (message.speaker === 'JUDGE') {
        indicatorEl.classList.add('bg-green-500');
    } else {
        indicatorEl.classList.add('bg-red-500');
    }
    
    // Set timestamp
    timestampEl.textContent = formatTimestamp(message.timestamp);
    
    // Set round
    roundEl.textContent = `${t('debate.round')} ${message.round}`;
    
    // Set full content - use textContent to preserve all text including newlines
    if (message.content) {
        textEl.textContent = message.content;
        console.log('Content set in modal, element text length:', textEl.textContent.length);
    } else {
        textEl.textContent = 'No content available';
        console.warn('Message has no content!');
    }
    
    // Show modal
    modal.classList.remove('hidden');
    modal.classList.add('flex');
}

function closeFullContentModal() {
    const modal = document.getElementById('full-content-modal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
}

// ========== Complete Debate ==========
async function completeDebate() {
    try {
        const response = await fetch(`/api/debates/${appState.sessionId}/complete`, {
            method: 'POST'
        });
        
        const data = await response.json();
        
        appState.sessionStatus = 'COMPLETED';
        updateSessionStatusUI();
        
        const winner = data.winner;
        const feedback = data.feedback;
        
        showToast(
            t('messages.debateComplete')
                .replace('{winner}', winner)
                .replace('{userScore}', data.finalScoreUser)
                .replace('{aiScore}', data.finalScoreAI)
                .replace('{feedback}', feedback.overall_assessment),
            'success'
        );
        
        // Stop timer
        stopTimer();
        
        // Disable input
        document.getElementById('argument-input').disabled = true;
        document.getElementById('btn-submit').disabled = true;
        document.getElementById('btn-preview').disabled = true;
        
        // Show new debate button
        document.getElementById('new-debate-section').classList.remove('hidden');
    } catch (error) {
        console.error('Error completing debate:', error);
    }
}

function resetDebate() {
    appState.sessionId = null;
    appState.sessionStatus = 'NOT_STARTED';
    appState.chatMessages = [];
    appState.currentRound = 1;
    appState.scores = { affirmativeTotal: 0, negativeTotal: 0 };
    appState.isPaused = false;
    
    // Reset playback controls
    document.getElementById('btn-start-debate').style.display = 'none';
    document.getElementById('btn-pause-debate').style.display = 'none';
    document.getElementById('btn-resume-debate').style.display = 'none';
    document.getElementById('btn-skip-debate').style.display = 'none';
    document.getElementById('new-debate-section').classList.add('hidden');
    document.getElementById('selected-topic-display').textContent = t('topics.noTopics');
    document.getElementById('chat-empty-state').style.display = 'block';
    document.getElementById('chat-messages').innerHTML = '<div id="chat-empty-state" class="text-center text-gray-500 italic py-8"><span id="chat-empty-text">' + t('chat.emptyState') + '</span></div>';
    document.getElementById('playback-status-text').textContent = 'Select a topic to begin';
    
    stopTimer();
    closeStreamingSSE();
    
    updateSessionStatusUI();
    updateUI();
    
    // Show topic selector
    openTopicModal();
}

// ========== UI Updates ==========
function updateUI() {
    // Update round display
    document.getElementById('current-round-num').textContent = appState.currentRound;
    const progress = (appState.currentRound / appState.maxRounds) * 100;
    document.getElementById('round-progress').style.width = `${progress}%`;
    
    // Update flow steps
    for (let i = 1; i <= 5; i++) {
        const flowStep = document.getElementById(`flow-round${i}`);
        flowStep.classList.remove('active', 'completed');
        if (i < appState.currentRound) {
            flowStep.classList.add('completed');
        } else if (i === appState.currentRound) {
            flowStep.classList.add('active');
        }
    }
    
    if (appState.currentRound > 5) {
        document.getElementById('flow-judging').classList.add('active');
    }
    
    // Update scores
    updateScores();
}

function updateScores() {
    document.getElementById('affirmative-score').textContent = appState.scores.affirmativeTotal.toFixed(2);
    document.getElementById('negative-score').textContent = appState.scores.negativeTotal.toFixed(2);
}

function updateSessionStatusUI() {
    const statusText = document.getElementById('status-text');
    statusText.textContent = t('states.' + appState.sessionStatus.toLowerCase().replace('_', ''));
    
    const statusDiv = document.getElementById('session-status');
    statusDiv.className = 'text-sm px-3 py-1 rounded border';
    
    if (appState.sessionStatus === 'IN_PROGRESS') {
        statusDiv.classList.add('bg-green-900', 'border-green-500', 'text-green-300');
    } else if (appState.sessionStatus === 'COMPLETED') {
        statusDiv.classList.add('bg-blue-900', 'border-blue-500', 'text-blue-300');
    } else {
        statusDiv.classList.add('bg-wasteland-darker', 'border-wasteland-gold', 'text-gray-400');
    }
}

function updateCharCounter() {
    const input = document.getElementById('argument-input');
    const counter = document.getElementById('char-counter');
    if (input && counter) {
        counter.textContent = `${input.value.length} / 500 ${t('debate.charCounter')}`;
    }
}

// ========== Timer ==========
function startTimer() {
    appState.timeRemaining = 180;
    updateTimerDisplay();
    
    appState.timerInterval = setInterval(() => {
        appState.timeRemaining--;
        updateTimerDisplay();
        
        if (appState.timeRemaining <= 0) {
            stopTimer();
        }
    }, 1000);
}

function stopTimer() {
    if (appState.timerInterval) {
        clearInterval(appState.timerInterval);
        appState.timerInterval = null;
    }
}

function updateTimerDisplay() {
    const minutes = Math.floor(appState.timeRemaining / 60);
    const seconds = appState.timeRemaining % 60;
    document.getElementById('time-display').textContent = 
        `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

// ========== Modal Management ==========
function openTopicModal() {
    loadTopics();
    document.getElementById('topic-modal').classList.remove('hidden');
    document.getElementById('topic-modal').classList.add('flex');
}

function closeTopicModal() {
    document.getElementById('topic-modal').classList.add('hidden');
    document.getElementById('topic-modal').classList.remove('flex');
}

// ========== Utilities ==========
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showToast(message, type = 'info') {
    alert(message);
}

// ========== Audio Playback Functions ==========

/**
 * Play audio for a message
 * @param {Object} message - Message object with id, content, speaker, etc.
 */
async function playMessageAudio(message) {
    try {
        // Stop any currently playing audio
        stopAudio();

        // Update UI to loading state
        updateAudioButtonState(message.id, 'loading');

        // Prepare request
        const requestBody = {
            text: message.content,
            role: message.speaker,
            language: appState.language
        };

        console.log('Requesting audio for message:', message.id);

        // Fetch audio from backend
        const response = await fetch('/api/voice/generate-speech', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        // Get audio blob
        const audioBlob = await response.blob();
        const audioUrl = URL.createObjectURL(audioBlob);

        // Create audio element
        const audio = new Audio(audioUrl);

        // Set up audio event listeners
        audio.addEventListener('play', () => {
            appState.audioPlayer.isPlaying = true;
            appState.audioPlayer.currentMessageId = message.id;
            updateAudioButtonState(message.id, 'playing');
        });

        audio.addEventListener('pause', () => {
            appState.audioPlayer.isPlaying = false;
            appState.audioPlayer.playbackPosition = audio.currentTime;
            updateAudioButtonState(message.id, 'paused');
        });

        audio.addEventListener('ended', () => {
            appState.audioPlayer.isPlaying = false;
            appState.audioPlayer.currentAudio = null;
            appState.audioPlayer.currentMessageId = null;
            appState.audioPlayer.playbackPosition = 0;
            updateAudioButtonState(message.id, 'idle');
            // Clean up blob URL
            URL.revokeObjectURL(audioUrl);
        });

        audio.addEventListener('error', (e) => {
            console.error('Audio playback error:', e);
            showToast(t('chat.audioError'), 'error');
            updateAudioButtonState(message.id, 'error');
            appState.audioPlayer.currentAudio = null;
            appState.audioPlayer.isPlaying = false;
        });

        // Store audio reference and play
        appState.audioPlayer.currentAudio = audio;
        audio.play();

    } catch (error) {
        console.error('Error playing audio:', error);
        showToast(t('chat.audioError'), 'error');
        updateAudioButtonState(message.id, 'error');
    }
}

/**
 * Stop current audio playback
 */
function stopAudio() {
    if (appState.audioPlayer.currentAudio) {
        const currentMessageId = appState.audioPlayer.currentMessageId;
        appState.audioPlayer.currentAudio.pause();
        appState.audioPlayer.currentAudio = null;
        appState.audioPlayer.isPlaying = false;
        appState.audioPlayer.currentMessageId = null;
        appState.audioPlayer.playbackPosition = 0;
        
        if (currentMessageId) {
            updateAudioButtonState(currentMessageId, 'idle');
        }
    }
}

/**
 * Pause current audio playback
 */
function pauseAudio() {
    if (appState.audioPlayer.currentAudio && appState.audioPlayer.isPlaying) {
        appState.audioPlayer.currentAudio.pause();
    }
}

/**
 * Resume audio playback from pause
 */
function resumeAudio() {
    if (appState.audioPlayer.currentAudio && !appState.audioPlayer.isPlaying) {
        appState.audioPlayer.currentAudio.play();
    }
}

/**
 * Toggle audio playback for a message
 * @param {Object} message - Message object
 */
function toggleAudioPlayback(message) {
    // If this message is currently playing, pause it
    if (appState.audioPlayer.currentMessageId === message.id && appState.audioPlayer.isPlaying) {
        pauseAudio();
    }
    // If this message is paused, resume it
    else if (appState.audioPlayer.currentMessageId === message.id && !appState.audioPlayer.isPlaying) {
        resumeAudio();
    }
    // Otherwise, play this message (stops any other playing audio)
    else {
        playMessageAudio(message);
    }
}

/**
 * Update audio button visual state
 * @param {string} messageId - ID of the message
 * @param {string} state - State: idle, loading, playing, paused, error
 */
function updateAudioButtonState(messageId, state) {
    const button = document.querySelector(`[data-audio-btn="${messageId}"]`);
    if (!button) return;

    // Remove all state classes
    button.classList.remove('audio-idle', 'audio-loading', 'audio-playing', 'audio-paused', 'audio-error');
    
    // Add current state class
    button.classList.add(`audio-${state}`);

    // Update icon and title
    const icon = button.querySelector('.audio-icon');
    if (!icon) return;

    switch (state) {
        case 'idle':
            icon.textContent = '🔊';
            button.title = t('chat.readAloud');
            break;
        case 'loading':
            icon.textContent = '⏳';
            button.title = t('chat.audioLoading');
            break;
        case 'playing':
            icon.textContent = '⏸';
            button.title = t('chat.pause');
            break;
        case 'paused':
            icon.textContent = '▶';
            button.title = t('chat.resume');
            break;
        case 'error':
            icon.textContent = '❌';
            button.title = t('chat.audioError');
            break;
    }
}

// ========== Event Listeners ==========
window.addEventListener('DOMContentLoaded', async () => {
    await loadTranslations();
    
    // Language toggle
    document.getElementById('btn-lang-toggle').addEventListener('click', toggleLanguage);
    
    // Topic selection
    document.getElementById('btn-select-topic').addEventListener('click', openTopicModal);
    document.getElementById('btn-close-modal').addEventListener('click', closeTopicModal);
    
    // AI Configuration modal
    document.getElementById('btn-close-config-modal').addEventListener('click', closeAIConfigModal);
    document.getElementById('btn-cancel-config').addEventListener('click', closeAIConfigModal);
    document.getElementById('btn-save-config').addEventListener('click', saveAIConfiguration);
    
    // Debate playback controls
    document.getElementById('btn-start-debate').addEventListener('click', startAutomatedDebate);
    document.getElementById('btn-pause-debate').addEventListener('click', pauseDebate);
    document.getElementById('btn-resume-debate').addEventListener('click', resumeDebate);
    document.getElementById('btn-skip-debate').addEventListener('click', skipToEnd);
    
    // New debate
    document.getElementById('btn-new-debate').addEventListener('click', resetDebate);
    
    // Full content modal
    document.getElementById('btn-close-full-content').addEventListener('click', closeFullContentModal);
    document.getElementById('btn-close-full-content-bottom').addEventListener('click', closeFullContentModal);
    
    // Click outside modal to close
    document.getElementById('full-content-modal').addEventListener('click', (e) => {
        if (e.target.id === 'full-content-modal') {
            closeFullContentModal();
        }
    });
    
    // Initial UI update
    updateUI();
    updateSessionStatusUI();
});
