import argparse
import numpy as np
import sys

N = 100;  # number of words to return


def generate():
    with open('vocab.txt', 'r') as f:
        words = [x.rstrip().split(' ')[0] for x in f.readlines()]

    with open('vectors.txt', 'r') as f:

        vectors = {}

        for line in f:
            vals = line.rstrip().split(' ')
            vectors[vals[0]] = [float(x) for x in vals[1:]]

    vocab_size = len(words)
    vocab = {w: idx for idx, w in enumerate(words)}
    ivocab = {idx: w for idx, w in enumerate(words)}

    vector_dim = len(vectors[ivocab[0]])
    W = np.zeros((vocab_size, vector_dim))

    for word, v in vectors.items():
        if word == '<unk>':
            continue

        W[vocab[word], :] = v

    # normalize each word vector to unit variance
    W_norm = np.zeros(W.shape)
    d = (np.sum(W ** 2, 1) ** (0.5))
    W_norm = (W.T / d).T
    return (W_norm, vocab, ivocab)

W, vocab, ivocab = generate()


def distance(input_term, W=W, vocab=vocab, ivocab=ivocab):

    for idx, term in enumerate(input_term.split(' ')):

        if term in vocab:
            print('Word: %s  Position in vocabulary: %i' % (term, vocab[term]))

            if idx == 0:
                vec_result = np.copy(W[vocab[term], :])

            else:
                vec_result += W[vocab[term], :] 

        else:
            print('Word: %s  Out of dictionary!\n' % term)
            return
    
    vec_norm = np.zeros(vec_result.shape)
    d = (np.sum(vec_result ** 2,) ** (0.5))
    vec_norm = (vec_result.T / d).T

    dist = np.dot(W, vec_norm.T)

    for term in input_term.split(' '):
        index = vocab[term]
        dist[index] = -np.Inf

    a = np.argsort(-dist)[:N]

    f = open('grassroot-universe-terms.txt', 'r')
    universe = f.read().split()
    ret_val = {}

    for x in a:

        if ivocab[x] in universe:
            new_val = {ivocab[x]: dist[x]}
            ret_val = {**ret_val, **new_val}
            
    return ret_val

